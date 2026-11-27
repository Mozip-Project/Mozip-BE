package com.mozip.worker.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStreamCommands.XClaimOptions;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.mozip.global.redis.MailTaskStream;

class MailTaskRetrySweeperTest {

	private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
	@SuppressWarnings("unchecked")
	private final StreamOperations<String, String, String> streamOperations = mock(StreamOperations.class);
	private final MailTaskProcessor processor = mock(MailTaskProcessor.class);
	private final MailTaskRetrySweeper sweeper = new MailTaskRetrySweeper(redisTemplate, processor);

	@Test
	@DisplayName("idle 시간이 60초 안이면 재시도도 DLQ 이동도 하지 않는다 (다른 컨슈머가 처리 중일 수 있음)")
	void skipsRecentlyDeliveredMessages() {
		stubPending(pendingMessage("1-1", Duration.ofSeconds(10), 1));

		sweeper.sweep();

		verify(streamOperations, never()).claim(any(), any(), any(), any(XClaimOptions.class));
	}

	@Test
	@DisplayName("idle 시간이 지났고 재시도 횟수가 남았으면 claim해서 다시 처리하고 DLQ로는 안 보낸다")
	void retriesStaleMessageUnderMaxAttempts() {
		stubPending(pendingMessage("1-1", Duration.ofSeconds(90), 3));
		MapRecord<String, String, String> claimed = claimedRecord();
		stubClaim(claimed);

		sweeper.sweep();

		verify(processor).process(claimed);
		verify(streamOperations, never()).add(any());
	}

	@Test
	@DisplayName("idle 시간이 지났고 총 전달 횟수(4)를 다 썼으면 DLQ로 옮기고 원본은 ACK한다")
	void movesExhaustedMessageToDeadLetter() {
		stubPending(pendingMessage("1-1", Duration.ofSeconds(90), 4));
		MapRecord<String, String, String> claimed = claimedRecord();
		stubClaim(claimed);

		sweeper.sweep();

		verify(streamOperations).add(argThat(record -> MailTaskStream.DLQ_KEY.equals(record.getStream())));
		verify(streamOperations).acknowledge(eq(MailTaskStream.CONSUMER_GROUP), eq(claimed));
		verify(processor, never()).process(any());
	}

	@Test
	@DisplayName("pending 조회가 실패해도 예외를 던지지 않는다")
	void swallowsPendingLookupFailure() {
		when(redisTemplate.<String, String>opsForStream()).thenReturn(streamOperations);
		when(streamOperations.pending(eq(MailTaskStream.KEY), any(Consumer.class), any(), anyLong()))
			.thenThrow(new RuntimeException("Redis 장애"));

		assertThat(sweeper).isNotNull();
		sweeper.sweep();
	}

	private void stubPending(PendingMessage... messages) {
		when(redisTemplate.<String, String>opsForStream()).thenReturn(streamOperations);
		when(streamOperations.pending(eq(MailTaskStream.KEY), any(Consumer.class), any(), anyLong()))
			.thenReturn(new PendingMessages(MailTaskStream.CONSUMER_GROUP, List.of(messages)));
	}

	private void stubClaim(MapRecord<String, String, String> record) {
		when(streamOperations.claim(eq(MailTaskStream.KEY), eq(MailTaskStream.CONSUMER_GROUP), any(),
			any(XClaimOptions.class))).thenReturn(List.of(record));
	}

	private MapRecord<String, String, String> claimedRecord() {
		return MapRecord.create(MailTaskStream.KEY, Map.of("to", "a@b.com", "subject", "제목", "body", "본문"));
	}

	private PendingMessage pendingMessage(String id, Duration elapsed, long deliveryCount) {
		return new PendingMessage(RecordId.of(id), Consumer.from(MailTaskStream.CONSUMER_GROUP, MailTaskStream.CONSUMER_NAME),
			elapsed, deliveryCount);
	}

}
