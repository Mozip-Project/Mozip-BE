package com.mozip.global.email;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;

// EmailVerificationCodeStore의 Redis 구현. namespace로 키를 나눠서 같은 메커니즘을 도메인마다(학교 인증, 단체 인증 등)
// 안 겹치게 재사용함. Spring 빈이 아니라 각 도메인의 리포지토리가 생성자에서 만들어 들고 쓴다
// (예: SchoolEmailVerificationCodeRepository가 namespace="school"로 하나 만들어 위임)
public class RedisEmailVerificationCodeStore implements EmailVerificationCodeStore {

	public static final Duration CODE_TTL = Duration.ofMinutes(5);
	public static final Duration REQUEST_COOLDOWN = Duration.ofSeconds(60);
	public static final Duration CONFIRMED_TTL = Duration.ofMinutes(10);
	public static final int MAX_ATTEMPTS = 5;

	private final StringRedisTemplate redisTemplate;
	private final String namespace;

	public RedisEmailVerificationCodeStore(StringRedisTemplate redisTemplate, String namespace) {
		this.redisTemplate = redisTemplate;
		this.namespace = namespace;
	}

	@Override
	public boolean isOnCooldown(String email) {
		return Boolean.TRUE.equals(redisTemplate.hasKey(key("cooldown", email)));
	}

	@Override
	public void markRequested(String email) {
		redisTemplate.opsForValue().set(key("cooldown", email), "1", REQUEST_COOLDOWN);
	}

	@Override
	public void saveCode(String email, String code) {
		redisTemplate.opsForValue().set(key("code", email), code, CODE_TTL);
		redisTemplate.delete(key("attempts", email));
		redisTemplate.delete(key("confirmed", email));
	}

	@Override
	public Optional<String> findCode(String email) {
		return Optional.ofNullable(redisTemplate.opsForValue().get(key("code", email)));
	}

	@Override
	public void deleteCode(String email) {
		redisTemplate.delete(key("code", email));
		redisTemplate.delete(key("attempts", email));
	}

	@Override
	public void recordWrongAttempt(String email) {
		String attemptsKey = key("attempts", email);
		Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
		redisTemplate.expire(attemptsKey, CODE_TTL);
		if (attempts != null && attempts >= MAX_ATTEMPTS) {
			deleteCode(email);
		}
	}

	@Override
	public boolean isConfirmed(String email) {
		return Boolean.TRUE.equals(redisTemplate.hasKey(key("confirmed", email)));
	}

	@Override
	public void markConfirmed(String email) {
		redisTemplate.opsForValue().set(key("confirmed", email), "1", CONFIRMED_TTL);
	}

	@Override
	public void deleteConfirmed(String email) {
		redisTemplate.delete(key("confirmed", email));
	}

	private String key(String part, String email) {
		return namespace + ":verify:" + part + ":" + email;
	}

}
