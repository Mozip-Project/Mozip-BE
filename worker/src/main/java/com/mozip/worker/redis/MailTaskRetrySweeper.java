package com.mozip.worker.redis;

import java.time.Duration;
import java.util.List;

import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisStreamCommands.XClaimOptions;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mozip.global.redis.MailTaskStream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// MailTaskStream.KEY의 pending(ACK 안 된) 항목을 주기적으로 살펴 재시도하거나 DLQ로 옮김.
// 재시도는 최대 3번(처음 시도 포함 총 3번 전달), 60초 이상 idle된 항목만 대상으로 함(처리 중인데 뺏는 걸 방지)
@Slf4j
@Component
@RequiredArgsConstructor
public class MailTaskRetrySweeper {

	private static final int MAX_DELIVERY_COUNT = 3;
	private static final Duration RETRY_IDLE = Duration.ofSeconds(60);
	private static final String CONSUMER_NAME = "worker-1";
	private static final int SWEEP_BATCH_SIZE = 100;

	private final StringRedisTemplate redisTemplate;
	private final MailTaskProcessor processor;

	@Scheduled(fixedDelay = 30_000)
	public void sweep() {
		PendingMessages pending;
		try {
			pending = redisTemplate.opsForStream().pending(
				MailTaskStream.KEY,
				Consumer.from(MailTaskStream.CONSUMER_GROUP, CONSUMER_NAME),
				Range.unbounded(),
				SWEEP_BATCH_SIZE);
		} catch (RuntimeException e) {
			log.warn("mail-tasks pending 조회 실패: cause={}", e.getClass().getSimpleName());
			return;
		}

		for (PendingMessage message : pending) {
			if (message.getElapsedTimeSinceLastDelivery().compareTo(RETRY_IDLE) < 0) {
				continue;
			}
			if (message.getTotalDeliveryCount() >= MAX_DELIVERY_COUNT) {
				moveToDeadLetter(message.getId());
			} else {
				retry(message.getId());
			}
		}
	}

	private void retry(RecordId id) {
		claim(id).forEach(processor::process);
	}

	private void moveToDeadLetter(RecordId id) {
		for (MapRecord<String, String, String> record : claim(id)) {
			redisTemplate.opsForStream().add(StreamRecords.newRecord().ofMap(record.getValue()).withStreamKey(MailTaskStream.DLQ_KEY));
			redisTemplate.opsForStream().acknowledge(MailTaskStream.CONSUMER_GROUP, record);
			log.error("메일 작업 최대 재시도 초과, DLQ로 이동: id={}, to={}", record.getId(), record.getValue().get("to"));
		}
	}

	private List<MapRecord<String, String, String>> claim(RecordId id) {
		return redisTemplate.<String, String>opsForStream().claim(MailTaskStream.KEY, MailTaskStream.CONSUMER_GROUP,
			CONSUMER_NAME, XClaimOptions.minIdle(RETRY_IDLE).ids(id));
	}

}
