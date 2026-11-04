package com.mozip.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.mozip.global.exception.BusinessException;
import com.mozip.global.security.jwt.TokenErrorCode;

class RefreshTokenRepositoryTest {

	@Test
	@DisplayName("Redis에 연결할 수 없으면 500 대신 AUTH_006(503)으로 바꿔 던진다")
	@SuppressWarnings("unchecked")
	void redisUnavailable() {
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.getAndDelete(anyString())).thenThrow(new RedisConnectionFailureException("연결 실패"));
		doThrow(new RedisConnectionFailureException("연결 실패"))
			.when(valueOperations).set(anyString(), anyString(), any(Duration.class));
		RefreshTokenRepository repository = new RefreshTokenRepository(redisTemplate);

		assertThatThrownBy(() -> repository.consume("token-id"))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(TokenErrorCode.TOKEN_STORE_UNAVAILABLE);
		assertThatThrownBy(() -> repository.save("token-id", 1L, Duration.ofDays(1)))
			.isInstanceOf(BusinessException.class);
	}

}
