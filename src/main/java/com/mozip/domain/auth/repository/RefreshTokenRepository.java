package com.mozip.domain.auth.repository;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

// Redis에 auth:refresh:{tokenId} → 회원 ID 저장 (기기별로 키 생성), TTL은 리프레시 토큰 만료 시간과 같음
// auth:user:{userId}:refresh Set에 회원의 tokenId를 모아 두어 회원 단위로 전체 폐기 가능
@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

	private static final String TOKEN_KEY_PREFIX = "auth:refresh:";
	private static final String USER_KEY_PREFIX = "auth:user:";
	private static final String USER_KEY_SUFFIX = ":refresh";

	private final StringRedisTemplate redisTemplate;

	public void save(String tokenId, Long userId, Duration ttl) {
		redisTemplate.opsForValue().set(tokenKey(tokenId), String.valueOf(userId), ttl);
		String userKey = userKey(userId);
		redisTemplate.opsForSet().add(userKey, tokenId);
		// 가장 최근 토큰 기준으로 Set 만료를 연장 (만료된 tokenId가 남아 있어도 삭제 시 무시됨)
		redisTemplate.expire(userKey, ttl);
	}

	// GETDEL로 조회와 동시에 삭제 (같은 토큰은 한 번만 사용 가능)
	public Optional<Long> consume(String tokenId) {
		String userId = redisTemplate.opsForValue().getAndDelete(tokenKey(tokenId));
		if (userId == null) {
			return Optional.empty();
		}
		redisTemplate.opsForSet().remove(userKey(Long.valueOf(userId)), tokenId);
		return Optional.of(Long.valueOf(userId));
	}

	public void delete(String tokenId) {
		consume(tokenId);
	}

	// 회원의 모든 기기 세션 폐기 (토큰 재사용 감지, 전체 로그아웃 등)
	public void deleteAllByUserId(Long userId) {
		String userKey = userKey(userId);
		Set<String> tokenIds = redisTemplate.opsForSet().members(userKey);
		if (tokenIds != null && !tokenIds.isEmpty()) {
			redisTemplate.delete(tokenIds.stream().map(RefreshTokenRepository::tokenKey).toList());
		}
		redisTemplate.delete(userKey);
	}

	private static String tokenKey(String tokenId) {
		return TOKEN_KEY_PREFIX + tokenId;
	}

	private static String userKey(Long userId) {
		return USER_KEY_PREFIX + userId + USER_KEY_SUFFIX;
	}

}
