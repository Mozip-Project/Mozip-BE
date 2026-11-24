package com.mozip.domain.notification.redis;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;

import com.mozip.domain.notification.sse.SseEmitterRegistry;

import tools.jackson.databind.ObjectMapper;

class NotificationRedisListenerTest {

	private final SseEmitterRegistry sseEmitterRegistry = mock(SseEmitterRegistry.class);
	private final NotificationRedisListener listener = new NotificationRedisListener(sseEmitterRegistry,
		new ObjectMapper());

	@Test
	@DisplayName("정상 메시지는 파싱해서 해당 userId로 push한다")
	void pushesParsedUserId() {
		String payload = "{\"userId\":42,\"id\":1}";

		listener.onMessage(message(payload), null);

		verify(sseEmitterRegistry).push(42L, payload);
	}

	@Test
	@DisplayName("userId가 없거나 JSON이 깨져 있어도 예외를 던지지 않고 메시지를 무시한다")
	void ignoresMalformedPayloadWithoutThrowing() {
		assertThatCode(() -> listener.onMessage(message("{\"id\":1}"), null)).doesNotThrowAnyException();
		assertThatCode(() -> listener.onMessage(message("not-json"), null)).doesNotThrowAnyException();

		verify(sseEmitterRegistry, never()).push(any(), any());
	}

	private DefaultMessage message(String payload) {
		return new DefaultMessage(NotificationChannel.EVENTS.getBytes(), payload.getBytes());
	}

}
