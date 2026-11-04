package com.mozip.domain.auth.repository;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

// Redis에 auth:refresh:{tokenId} → 회원 ID 저장 (기기별로 키 생성)
// TTL은 리프레시 토큰 만료 시간과 같음
@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

	private static final String KEY_PREFIX = "auth:refresh:";

	private final StringRedisTemplate redisTemplate;

	public void save(String tokenId, Long userId, Duration ttl) {
		redisTemplate.opsForValue().set(key(tokenId), String.valueOf(userId), ttl);
	}

	// GETDEL로 조회와 동시에 삭제 (같은 토큰은 한 번만 사용 가능)
	public Optional<Long> consume(String tokenId) {
		return Optional.ofNullable(redisTemplate.opsForValue().getAndDelete(key(tokenId)))
			.map(Long::valueOf);
	}

	public void delete(String tokenId) {
		redisTemplate.delete(key(tokenId));
	}

	private String key(String tokenId) {
		return KEY_PREFIX + tokenId;
	}

}
