package com.mozip.worker.redis;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;

import com.mozip.global.redis.MailTaskStream;

import lombok.extern.slf4j.Slf4j;

// MailTaskStream.KEY를 polling하는 컨테이너. 컨슈머 그룹이 없으면 MKSTREAM으로 같이 만듦(이미 있으면 BUSYGROUP 무시).
// BUSYGROUP이 아닌 다른 에러(Redis 연결 장애 등)는 삼키지 않고 기동을 실패시킴
@Slf4j
@Configuration
public class MailTaskStreamConfig {

	@Bean(initMethod = "start", destroyMethod = "stop")
	public StreamMessageListenerContainer<String, MapRecord<String, String, String>> mailTaskStreamContainer(
		StringRedisTemplate redisTemplate,
		MailTaskStreamListener listener
	) {
		ensureConsumerGroup(redisTemplate);

		StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
			StreamMessageListenerContainerOptions.builder()
				.pollTimeout(Duration.ofSeconds(5))
				.build();
		StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
			StreamMessageListenerContainer.create(redisTemplate.getConnectionFactory(), options);

		container.receive(
			Consumer.from(MailTaskStream.CONSUMER_GROUP, MailTaskStream.CONSUMER_NAME),
			StreamOffset.create(MailTaskStream.KEY, ReadOffset.lastConsumed()),
			listener);

		return container;
	}

	void ensureConsumerGroup(StringRedisTemplate redisTemplate) {
		try {
			redisTemplate.opsForStream().createGroup(MailTaskStream.KEY, ReadOffset.from("0"), MailTaskStream.CONSUMER_GROUP);
		} catch (DataAccessException e) {
			if (isBusyGroup(e)) {
				// 그룹이 이미 있으면 BUSYGROUP 에러. 정상 상황이라 무시
				log.debug("mail-tasks 컨슈머 그룹 생성 스킵(이미 있음)");
				return;
			}
			// BUSYGROUP이 아니면 Redis 연결 장애 등 실제 문제. 조용히 넘기면 그룹 없이 기동돼 이후 polling마다
			// NOGROUP 에러가 반복되므로 여기서 바로 실패시킴
			log.error("mail-tasks 컨슈머 그룹 생성 실패: cause={}", e.getClass().getSimpleName(), e);
			throw e;
		}
	}

	private static boolean isBusyGroup(Throwable e) {
		for (Throwable current = e; current != null; current = current.getCause()) {
			if (current.getMessage() != null && current.getMessage().contains("BUSYGROUP")) {
				return true;
			}
		}
		return false;
	}

}
