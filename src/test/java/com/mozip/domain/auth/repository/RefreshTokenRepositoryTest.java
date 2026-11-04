package com.mozip.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.mozip.global.exception.BusinessException;
import com.mozip.global.security.jwt.TokenErrorCode;

class RefreshTokenRepositoryTest {

	private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
	@SuppressWarnings("unchecked")
	private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
	@SuppressWarnings("unchecked")
	private final SetOperations<String, String> setOperations = mock(SetOperations.class);
	private final RefreshTokenRepository repository = new RefreshTokenRepository(redisTemplate);

	@Test
	@DisplayName("Redis에 연결할 수 없으면 500 대신 AUTH_006(503)으로 바꿔 던진다")
	void redisUnavailable() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.getAndDelete(anyString())).thenThrow(new RedisConnectionFailureException("연결 실패"));
		doThrow(new RedisConnectionFailureException("연결 실패"))
			.when(valueOperations).set(anyString(), anyString(), any(Duration.class));

		assertThatThrownBy(() -> repository.consume("token-id"))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(TokenErrorCode.TOKEN_STORE_UNAVAILABLE);
		assertThatThrownBy(() -> repository.save("token-id", 1L, Duration.ofDays(1)))
			.isInstanceOf(BusinessException.class);
	}

	@Test
	@DisplayName("GETDEL로 토큰을 지운 뒤의 정리 작업이 실패해도 소비는 성공으로 처리한다")
	void cleanupFailureAfterConsume() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(redisTemplate.opsForSet()).thenReturn(setOperations);
		when(valueOperations.getAndDelete("auth:refresh:token-id")).thenReturn("7");
		when(setOperations.remove(anyString(), any())).thenThrow(new RedisConnectionFailureException("연결 실패"));

		assertThat(repository.consume("token-id")).contains(7L);
	}

}
