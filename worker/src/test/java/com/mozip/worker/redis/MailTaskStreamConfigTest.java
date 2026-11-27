package com.mozip.worker.redis;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.mozip.global.redis.MailTaskStream;

class MailTaskStreamConfigTest {

	private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
	@SuppressWarnings("unchecked")
	private final StreamOperations<String, String, String> streamOperations = mock(StreamOperations.class);
	private final MailTaskStreamConfig config = new MailTaskStreamConfig();

	@Test
	@DisplayName("그룹이 이미 있어서 BUSYGROUP 에러면 조용히 넘어간다")
	void ignoresBusyGroupError() {
		stubCreateGroupThrows(new RedisSystemException("Error in execution",
			new RuntimeException("BUSYGROUP Consumer Group name already exists")));

		assertThatCode(() -> config.ensureConsumerGroup(redisTemplate)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("BUSYGROUP이 아닌 에러(Redis 연결 장애 등)는 삼키지 않고 다시 던진다")
	void rethrowsNonBusyGroupError() {
		stubCreateGroupThrows(new DataAccessResourceFailureException("연결 실패"));

		assertThatThrownBy(() -> config.ensureConsumerGroup(redisTemplate))
			.isInstanceOf(DataAccessResourceFailureException.class);
	}

	private void stubCreateGroupThrows(RuntimeException exception) {
		when(redisTemplate.<String, String>opsForStream()).thenReturn(streamOperations);
		doThrow(exception).when(streamOperations)
			.createGroup(eq(MailTaskStream.KEY), any(ReadOffset.class), eq(MailTaskStream.CONSUMER_GROUP));
	}

}
