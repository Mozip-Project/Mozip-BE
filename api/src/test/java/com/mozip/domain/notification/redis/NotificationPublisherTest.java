package com.mozip.domain.notification.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.mozip.domain.notification.entity.NotificationType;
import com.mozip.domain.notification.event.NotificationCreatedEvent;

class NotificationPublisherTest {

	private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
	private final NotificationPublisher publisher = new NotificationPublisher(redisTemplate);

	@Test
	@DisplayName("createdAt을 GraphQL DateTime 스칼라와 같은 서울 오프셋 형식으로 직렬화한다")
	void serializesCreatedAtWithSeoulOffset() {
		LocalDateTime createdAt = LocalDateTime.of(2026, 11, 21, 10, 0, 0);
		NotificationCreatedEvent event = new NotificationCreatedEvent(1L, 2L, NotificationType.POSTING_NOTICE,
			"제목", "/link", null, createdAt);

		publisher.publish(event);

		assertThat(sentPayload()).contains("\"createdAt\":\"2026-11-21T10:00:00+09:00\"");
	}

	@Test
	@DisplayName("organizationId가 null이면 그대로 null로 직렬화한다")
	void serializesNullOrganizationId() {
		NotificationCreatedEvent event = new NotificationCreatedEvent(1L, 2L, NotificationType.POSTING_NOTICE,
			"제목", "/link", null, LocalDateTime.now());

		publisher.publish(event);

		assertThat(sentPayload()).contains("\"organizationId\":null");
	}

	private String sentPayload() {
		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
		verify(redisTemplate).convertAndSend(eq(NotificationChannel.EVENTS), captor.capture());
		return captor.getValue();
	}

}
