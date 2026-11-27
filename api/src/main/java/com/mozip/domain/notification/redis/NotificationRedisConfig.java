package com.mozip.domain.notification.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.util.ErrorHandler;

import lombok.extern.slf4j.Slf4j;

// worker의 RedisListenerConfig와 같은 모양이지만, SSE 연결이 각 api 파드에 로컬로 있어 api에 둔다
@Slf4j
@Configuration
public class NotificationRedisConfig {

	@Bean
	public RedisMessageListenerContainer notificationRedisMessageListenerContainer(
		RedisConnectionFactory connectionFactory,
		NotificationRedisListener notificationRedisListener) {
		RedisMessageListenerContainer container = new RedisMessageListenerContainer();
		container.setConnectionFactory(connectionFactory);
		container.addMessageListener(notificationRedisListener, new ChannelTopic(NotificationChannel.EVENTS));
		// 리스너 안에서도 예외를 다 잡아두지만, 혹시 빠져나온 예외가 구독 자체를 끊지 않도록 한 번 더 막음
		container.setErrorHandler(errorHandler());
		return container;
	}

	private ErrorHandler errorHandler() {
		return throwable -> log.warn("알림 Redis 리스너에서 처리되지 않은 예외: cause={}", throwable.getClass().getSimpleName());
	}

}
