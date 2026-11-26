package com.mozip.domain.school.repository;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

// Redis에 school:verify:code:{email} → 인증번호 저장(TTL 5분), 같은 TTL로 틀린 횟수(attempts)를 같이 관리.
// cooldown 키로 재요청 간격을 제한함
@Repository
@RequiredArgsConstructor
public class SchoolEmailVerificationCodeRepository {

	public static final Duration CODE_TTL = Duration.ofMinutes(5);
	public static final Duration REQUEST_COOLDOWN = Duration.ofSeconds(60);
	public static final int MAX_ATTEMPTS = 5;

	private static final String CODE_KEY_PREFIX = "school:verify:code:";
	private static final String ATTEMPTS_KEY_PREFIX = "school:verify:attempts:";
	private static final String COOLDOWN_KEY_PREFIX = "school:verify:cooldown:";

	private final StringRedisTemplate redisTemplate;

	// 재요청 간격 안이면 true (SCHOOL_429)
	public boolean isOnCooldown(String email) {
		return Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey(email)));
	}

	public void markRequested(String email) {
		redisTemplate.opsForValue().set(cooldownKey(email), "1", REQUEST_COOLDOWN);
	}

	// 인증번호를 새로 저장하며 이전 틀린 횟수는 초기화
	public void saveCode(String email, String code) {
		redisTemplate.opsForValue().set(codeKey(email), code, CODE_TTL);
		redisTemplate.delete(attemptsKey(email));
	}

	public Optional<String> findCode(String email) {
		return Optional.ofNullable(redisTemplate.opsForValue().get(codeKey(email)));
	}

	public void deleteCode(String email) {
		redisTemplate.delete(codeKey(email));
		redisTemplate.delete(attemptsKey(email));
	}

	// 틀린 횟수를 올리고, MAX_ATTEMPTS를 넘으면 코드를 바로 지움(다음 확인은 CODE_EXPIRED로 응답됨)
	public void recordWrongAttempt(String email) {
		Long attempts = redisTemplate.opsForValue().increment(attemptsKey(email));
		redisTemplate.expire(attemptsKey(email), CODE_TTL);
		if (attempts != null && attempts >= MAX_ATTEMPTS) {
			deleteCode(email);
		}
	}

	private static String codeKey(String email) {
		return CODE_KEY_PREFIX + email;
	}

	private static String attemptsKey(String email) {
		return ATTEMPTS_KEY_PREFIX + email;
	}

	private static String cooldownKey(String email) {
		return COOLDOWN_KEY_PREFIX + email;
	}

}
