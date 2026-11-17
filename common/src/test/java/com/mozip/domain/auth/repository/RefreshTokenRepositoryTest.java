package com.mozip.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

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
		when(redisTemplate.execute(ArgumentMatchers.<RedisScript<String>>any(), anyList(), any()))
			.thenThrow(new RedisConnectionFailureException("연결 실패"));
		when(valueOperations.getAndDelete(anyString())).thenThrow(new RedisConnectionFailureException("연결 실패"));
		doThrow(new RedisConnectionFailureException("연결 실패"))
			.when(valueOperations).set(anyString(), anyString(), any(Duration.class));

		assertThatThrownBy(() -> repository.consume("token-id"))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(TokenErrorCode.TOKEN_STORE_UNAVAILABLE);
		assertThatThrownBy(() -> repository.delete("token-id"))
			.isInstanceOf(BusinessException.class);
		assertThatThrownBy(() -> repository.save("token-id", 1L, Duration.ofDays(1)))
			.isInstanceOf(BusinessException.class);
	}

	@Test
	@DisplayName("토큰을 지운 뒤의 Set 정리가 실패해도 소비와 삭제는 성공으로 처리한다")
	void setCleanupFailureAfterDelete() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(redisTemplate.opsForSet()).thenReturn(setOperations);
		when(redisTemplate.execute(ArgumentMatchers.<RedisScript<String>>any(), anyList(), any())).thenReturn("7");
		when(valueOperations.getAndDelete("auth:refresh:token-id")).thenReturn("7");
		when(setOperations.remove(anyString(), any())).thenThrow(new RedisConnectionFailureException("연결 실패"));

		assertThat(repository.consume("token-id")).contains(7L);
		assertThatCode(() -> repository.delete("token-id")).doesNotThrowAnyException();
	}

}
