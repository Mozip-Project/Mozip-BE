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

// MailTaskStream.KEY를 polling하는 컨테이너. 컨슈머 그룹이 없으면 MKSTREAM으로 같이 만듦(이미 있으면 BUSYGROUP 무시)
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

	private void ensureConsumerGroup(StringRedisTemplate redisTemplate) {
		try {
			redisTemplate.opsForStream().createGroup(MailTaskStream.KEY, ReadOffset.from("0"), MailTaskStream.CONSUMER_GROUP);
		} catch (DataAccessException e) {
			// 그룹이 이미 있으면 BUSYGROUP 에러. 정상 상황이라 무시
			log.debug("mail-tasks 컨슈머 그룹 생성 스킵(이미 있음): cause={}", e.getClass().getSimpleName());
		}
	}

}
