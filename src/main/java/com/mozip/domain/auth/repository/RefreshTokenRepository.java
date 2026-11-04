package com.mozip.domain.auth.repository;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
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

	// GETDEL로 조회와 동시에 삭제 (같은 토큰은 한 번만 사용 가능)
	public Optional<Long> consume(String tokenId) {
		String userId = execute(() -> redisTemplate.opsForValue().getAndDelete(tokenKey(tokenId)));
		if (userId == null) {
			return Optional.empty();
		}
		// 토큰은 이미 지워졌으므로 뒤의 정리가 실패해도 소비는 성공으로 처리
		// (Set 항목은 TTL로 만료되고, 마커가 없으면 중복 요청이 재사용으로 판정되는 정도)
		try {
			redisTemplate.opsForSet().remove(userKey(Long.valueOf(userId)), tokenId);
			redisTemplate.opsForValue().set(consumedKey(tokenId), userId, CONSUMED_GRACE);
		} catch (DataAccessException e) {
			log.warn("리프레시 토큰 소비 후 정리 실패: tokenId={}, cause={}", tokenId, e.getClass().getSimpleName());
		}
		return Optional.of(Long.valueOf(userId));
	}

	// 방금 소비된 토큰인지 확인 (true면 같은 쿠키로 동시에 보낸 중복 요청)
	public boolean wasRecentlyConsumed(String tokenId) {
		return execute(() -> Boolean.TRUE.equals(redisTemplate.hasKey(consumedKey(tokenId))));
	}

	// 로그아웃용 삭제 (소비 마커를 남기지 않아 이후 재사용은 탈취로 판정)
	public void delete(String tokenId) {
		execute(() -> {
			String userId = redisTemplate.opsForValue().getAndDelete(tokenKey(tokenId));
			if (userId != null) {
				redisTemplate.opsForSet().remove(userKey(Long.valueOf(userId)), tokenId);
			}
			return null;
		});
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
