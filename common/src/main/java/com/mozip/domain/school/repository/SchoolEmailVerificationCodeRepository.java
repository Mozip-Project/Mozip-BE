package com.mozip.domain.school.repository;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import com.mozip.domain.school.entity.SchoolEmailVerificationStatus;

import lombok.RequiredArgsConstructor;

// Redis에 school:verify:code:{email} → 인증번호 저장(TTL 5분), 같은 TTL로 틀린 횟수(attempts)를 같이 관리.
// cooldown 키로 재요청 간격을 제한함. status 키는 worker가 실제로 메일을 보냈는지 api/클라이언트가 조회하는 용도.
// confirmed 키는 verifySchoolEmail로 코드 확인까지 끝낸 메일을 표시(가입 전까지 10분 유지)
@Repository
@RequiredArgsConstructor
public class SchoolEmailVerificationCodeRepository {

	public static final Duration CODE_TTL = Duration.ofMinutes(5);
	public static final Duration REQUEST_COOLDOWN = Duration.ofSeconds(60);
	public static final Duration CONFIRMED_TTL = Duration.ofMinutes(10);
	public static final int MAX_ATTEMPTS = 5;

	private static final String CODE_KEY_PREFIX = "school:verify:code:";
	private static final String ATTEMPTS_KEY_PREFIX = "school:verify:attempts:";
	private static final String COOLDOWN_KEY_PREFIX = "school:verify:cooldown:";
	private static final String STATUS_KEY_PREFIX = "school:verify:status:";
	private static final String CONFIRMED_KEY_PREFIX = "school:verify:confirmed:";

	private final StringRedisTemplate redisTemplate;

	// 재요청 간격 안이면 true (SCHOOL_429)
	public boolean isOnCooldown(String email) {
		return Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey(email)));
	}

	public void markRequested(String email) {
		redisTemplate.opsForValue().set(cooldownKey(email), "1", REQUEST_COOLDOWN);
	}

	// 인증번호를 새로 저장하며 이전 틀린 횟수와 예전 코드로 받은 confirmed 상태는 초기화
	public void saveCode(String email, String code) {
		redisTemplate.opsForValue().set(codeKey(email), code, CODE_TTL);
		redisTemplate.delete(attemptsKey(email));
		redisTemplate.delete(confirmedKey(email));
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

	// api가 Redis 발행까지 성공한 뒤에 호출. worker가 이어서 markSent/markFailed로 덮어씀
	public void markPending(String email) {
		redisTemplate.opsForValue().set(statusKey(email), SchoolEmailVerificationStatus.PENDING.name(), CODE_TTL);
	}

	public void markSent(String email) {
		redisTemplate.opsForValue().set(statusKey(email), SchoolEmailVerificationStatus.SENT.name(), CODE_TTL);
	}

	public void markFailed(String email) {
		redisTemplate.opsForValue().set(statusKey(email), SchoolEmailVerificationStatus.FAILED.name(), CODE_TTL);
	}

	// 요청한 적 없거나 TTL이 지났으면 empty (클라이언트는 이걸 "아직 모름"으로 다룸)
	public Optional<SchoolEmailVerificationStatus> findDeliveryStatus(String email) {
		return Optional.ofNullable(redisTemplate.opsForValue().get(statusKey(email)))
			.map(SchoolEmailVerificationStatus::valueOf);
	}

	// verifySchoolEmail로 코드 확인에 성공하면 호출. 가입(verifyForSignup)이 끝나면 지움
	public boolean isConfirmed(String email) {
		return Boolean.TRUE.equals(redisTemplate.hasKey(confirmedKey(email)));
	}

	public void markConfirmed(String email) {
		redisTemplate.opsForValue().set(confirmedKey(email), "1", CONFIRMED_TTL);
	}

	public void deleteConfirmed(String email) {
		redisTemplate.delete(confirmedKey(email));
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

	private static String statusKey(String email) {
		return STATUS_KEY_PREFIX + email;
	}

	private static String confirmedKey(String email) {
		return CONFIRMED_KEY_PREFIX + email;
	}

}
