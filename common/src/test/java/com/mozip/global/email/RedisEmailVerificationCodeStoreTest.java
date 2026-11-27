package com.mozip.global.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisEmailVerificationCodeStoreTest {

	private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
	@SuppressWarnings("unchecked")
	private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

	private final RedisEmailVerificationCodeStore schoolStore =
		new RedisEmailVerificationCodeStore(redisTemplate, "school");
	private final RedisEmailVerificationCodeStore organizationStore =
		new RedisEmailVerificationCodeStore(redisTemplate, "organization");

	@Test
	@DisplayName("namespace가 다르면 같은 email이라도 다른 Redis 키를 쓴다 (학교 인증, 단체 인증이 안 겹침)")
	void differentNamespacesUseDifferentKeys() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);

		schoolStore.saveCode("student@school.ac.kr", "111111");
		organizationStore.saveCode("student@school.ac.kr", "222222");

		verify(valueOperations).set(eq("school:verify:code:student@school.ac.kr"), eq("111111"),
			eq(RedisEmailVerificationCodeStore.CODE_TTL));
		verify(valueOperations).set(eq("organization:verify:code:student@school.ac.kr"), eq("222222"),
			eq(RedisEmailVerificationCodeStore.CODE_TTL));
	}

	@Test
	@DisplayName("saveCode는 이전 틀린 횟수와 confirmed 표시를 같이 지운다")
	void saveCodeClearsAttemptsAndConfirmed() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);

		schoolStore.saveCode("a@b.com", "123456");

		verify(redisTemplate).delete("school:verify:attempts:a@b.com");
		verify(redisTemplate).delete("school:verify:confirmed:a@b.com");
	}

	@Test
	@DisplayName("recordWrongAttempt는 MAX_ATTEMPTS를 넘으면 코드를 지운다")
	void recordWrongAttemptDeletesCodeAfterMaxAttempts() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.increment("school:verify:attempts:a@b.com"))
			.thenReturn((long)RedisEmailVerificationCodeStore.MAX_ATTEMPTS);

		schoolStore.recordWrongAttempt("a@b.com");

		verify(redisTemplate).delete("school:verify:code:a@b.com");
	}

	@Test
	@DisplayName("recordWrongAttempt는 MAX_ATTEMPTS 전까지는 코드를 지우지 않는다")
	void recordWrongAttemptKeepsCodeBeforeMaxAttempts() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.increment("school:verify:attempts:a@b.com")).thenReturn(1L);

		schoolStore.recordWrongAttempt("a@b.com");

		verify(redisTemplate, never()).delete("school:verify:code:a@b.com");
	}

	@Test
	@DisplayName("markConfirmed/isConfirmed/deleteConfirmed는 namespace별 confirmed 키를 쓴다")
	void confirmedLifecycleUsesNamespacedKey() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(redisTemplate.hasKey("school:verify:confirmed:a@b.com")).thenReturn(true);

		schoolStore.markConfirmed("a@b.com");
		boolean confirmed = schoolStore.isConfirmed("a@b.com");
		schoolStore.deleteConfirmed("a@b.com");

		verify(valueOperations).set("school:verify:confirmed:a@b.com", "1",
			RedisEmailVerificationCodeStore.CONFIRMED_TTL);
		assertThat(confirmed).isTrue();
		verify(redisTemplate).delete("school:verify:confirmed:a@b.com");
	}

	@Test
	@DisplayName("isOnCooldown은 cooldown 키 존재 여부를 그대로 반환한다")
	void isOnCooldownReflectsKeyExistence() {
		when(redisTemplate.hasKey("school:verify:cooldown:a@b.com")).thenReturn(false);

		assertThat(schoolStore.isOnCooldown("a@b.com")).isFalse();
	}

}
