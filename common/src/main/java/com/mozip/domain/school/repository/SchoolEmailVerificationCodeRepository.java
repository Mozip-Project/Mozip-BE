package com.mozip.domain.school.repository;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import com.mozip.domain.school.entity.SchoolEmailVerificationStatus;
import com.mozip.global.email.EmailVerificationCodeStore;
import com.mozip.global.email.RedisEmailVerificationCodeStore;

// 학교 인증 전용 래퍼. 인증번호 요청/검증/confirmed 상태(cooldown, code, attempts, confirmed)는
// EmailVerificationCodeStore(공통, namespace="school")에 맡기고, worker 발송 상태(status)만 여기서 따로 관리함.
// 같은 메커니즘(메일 발송 → 코드 확인)이 필요한 다른 도메인(단체 인증 등)도 EmailVerificationCodeStore를 재사용할 수 있음
@Repository
public class SchoolEmailVerificationCodeRepository implements EmailVerificationCodeStore {

	private static final String NAMESPACE = "school";
	private static final String STATUS_KEY_PREFIX = "school:verify:status:";

	public static final Duration CODE_TTL = RedisEmailVerificationCodeStore.CODE_TTL;
	public static final Duration REQUEST_COOLDOWN = RedisEmailVerificationCodeStore.REQUEST_COOLDOWN;
	public static final Duration CONFIRMED_TTL = RedisEmailVerificationCodeStore.CONFIRMED_TTL;
	public static final int MAX_ATTEMPTS = RedisEmailVerificationCodeStore.MAX_ATTEMPTS;

	private final EmailVerificationCodeStore codeStore;
	private final StringRedisTemplate redisTemplate;

	public SchoolEmailVerificationCodeRepository(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
		this.codeStore = new RedisEmailVerificationCodeStore(redisTemplate, NAMESPACE);
	}

	@Override
	public boolean isOnCooldown(String email) {
		return codeStore.isOnCooldown(email);
	}

	@Override
	public void markRequested(String email) {
		codeStore.markRequested(email);
	}

	@Override
	public void saveCode(String email, String code) {
		codeStore.saveCode(email, code);
	}

	@Override
	public Optional<String> findCode(String email) {
		return codeStore.findCode(email);
	}

	@Override
	public void deleteCode(String email) {
		codeStore.deleteCode(email);
	}

	@Override
	public void recordWrongAttempt(String email) {
		codeStore.recordWrongAttempt(email);
	}

	@Override
	public boolean isConfirmed(String email) {
		return codeStore.isConfirmed(email);
	}

	@Override
	public void markConfirmed(String email) {
		codeStore.markConfirmed(email);
	}

	@Override
	public void deleteConfirmed(String email) {
		codeStore.deleteConfirmed(email);
	}

	// 아래는 학교 인증에서만 쓰는 발송 상태. 공통 인터페이스에는 없음(도메인마다 발송 방식이 다를 수 있어서)

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

	private static String statusKey(String email) {
		return STATUS_KEY_PREFIX + email;
	}

}
