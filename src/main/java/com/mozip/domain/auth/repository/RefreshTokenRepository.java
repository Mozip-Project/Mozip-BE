package com.mozip.domain.auth.repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import com.mozip.global.exception.BusinessException;
import com.mozip.global.security.jwt.TokenErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Redis에 auth:refresh:{tokenId} → 회원 ID 저장 (기기별로 키 생성), TTL은 리프레시 토큰 만료 시간과 같음
// auth:user:{userId}:refresh Set에 회원의 tokenId를 모아 두어 회원 단위로 전체 폐기 가능
@Slf4j
@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

	// 소비된 토큰을 짧게 기억해 두는 마커 (동시에 온 중복 재발급 요청을 탈취로 오인하지 않기 위함)
	public static final String CONSUMED_KEY_PREFIX = "auth:refresh:consumed:";
	public static final Duration CONSUMED_GRACE = Duration.ofSeconds(10);

	private static final String TOKEN_KEY_PREFIX = "auth:refresh:";
	private static final String USER_KEY_PREFIX = "auth:user:";
	private static final String USER_KEY_SUFFIX = ":refresh";

	// KEYS[1]=토큰 키, KEYS[2]=소비 마커 키, ARGV[1]=마커 TTL(ms), 반환=회원 ID 또는 nil
	private static final RedisScript<String> CONSUME_SCRIPT = RedisScript.of("""
		local userId = redis.call('GET', KEYS[1])
		if not userId then
			return nil
		end
		redis.call('DEL', KEYS[1])
		redis.call('SET', KEYS[2], userId, 'PX', ARGV[1])
		return userId
		""", String.class);

	private final StringRedisTemplate redisTemplate;

	public void save(String tokenId, Long userId, Duration ttl) {
		execute(() -> {
			redisTemplate.opsForValue().set(tokenKey(tokenId), String.valueOf(userId), ttl);
			String userKey = userKey(userId);
			redisTemplate.opsForSet().add(userKey, tokenId);
			// 가장 최근 토큰 기준으로 Set 만료를 연장 (만료된 tokenId가 남아 있어도 삭제 시 무시됨)
			redisTemplate.expire(userKey, ttl);
			return null;
		});
	}

	// 조회, 삭제, 소비 마커 저장을 Lua 스크립트 하나로 실행 (같은 토큰은 한 번만 사용 가능)
	// 세 명령 사이에 다른 요청이 끼어들면 마커가 없어 중복 요청이 탈취로 판정되므로 원자적으로 묶음
	public Optional<Long> consume(String tokenId) {
		String userId = execute(() -> redisTemplate.execute(CONSUME_SCRIPT,
			List.of(tokenKey(tokenId), consumedKey(tokenId)), String.valueOf(CONSUMED_GRACE.toMillis())));
		if (userId == null) {
			return Optional.empty();
		}
		removeFromUserSet(userId, tokenId);
		return Optional.of(Long.valueOf(userId));
	}

	// 방금 소비된 토큰인지 확인 (true면 같은 쿠키로 동시에 보낸 중복 요청)
	public boolean wasRecentlyConsumed(String tokenId) {
		return execute(() -> Boolean.TRUE.equals(redisTemplate.hasKey(consumedKey(tokenId))));
	}

	// 로그아웃용 삭제 (소비 마커를 남기지 않아 이후 재사용은 탈취로 판정)
	public void delete(String tokenId) {
		String userId = execute(() -> redisTemplate.opsForValue().getAndDelete(tokenKey(tokenId)));
		if (userId != null) {
			removeFromUserSet(userId, tokenId);
		}
	}

	// 토큰은 이미 지워진 뒤라 Set 정리 실패는 삭제 성공을 뒤집지 않음 (남은 항목은 TTL로 만료)
	// 덕분에 AUTH_006은 토큰을 지우기 전의 실패에서만 발생
	private void removeFromUserSet(String userId, String tokenId) {
		try {
			redisTemplate.opsForSet().remove(userKey(Long.valueOf(userId)), tokenId);
		} catch (DataAccessException e) {
			log.warn("리프레시 토큰 Set 정리 실패: tokenId={}, cause={}", tokenId, e.getClass().getSimpleName());
		}
	}

	// 회원의 모든 기기 세션 폐기 (토큰 재사용 감지, 전체 로그아웃 등)
	public void deleteAllByUserId(Long userId) {
		execute(() -> {
			String userKey = userKey(userId);
			Set<String> tokenIds = redisTemplate.opsForSet().members(userKey);
			if (tokenIds != null && !tokenIds.isEmpty()) {
				redisTemplate.delete(tokenIds.stream().map(RefreshTokenRepository::tokenKey).toList());
			}
			redisTemplate.delete(userKey);
			return null;
		});
	}

	// Redis 연결 실패 등은 500 대신 503(AUTH_006)으로 응답해 프론트가 재시도를 안내할 수 있게 함
	private static <T> T execute(Supplier<T> action) {
		try {
			return action.get();
		} catch (DataAccessException e) {
			throw new BusinessException(TokenErrorCode.TOKEN_STORE_UNAVAILABLE)
				.addContext("cause", e.getClass().getSimpleName());
		}
	}

	private static String tokenKey(String tokenId) {
		return TOKEN_KEY_PREFIX + tokenId;
	}

	private static String consumedKey(String tokenId) {
		return CONSUMED_KEY_PREFIX + tokenId;
	}

	private static String userKey(Long userId) {
		return USER_KEY_PREFIX + userId + USER_KEY_SUFFIX;
	}

}
