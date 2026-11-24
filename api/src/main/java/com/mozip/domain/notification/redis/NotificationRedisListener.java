package com.mozip.domain.notification.redis;

import java.util.Map;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import com.mozip.domain.notification.sse.SseEmitterRegistry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

// mozip:notification:events로 들어오는 메시지를 받아, 이 파드가 들고 있는 SSE 연결에만 전달
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRedisListener implements MessageListener {

	private final SseEmitterRegistry sseEmitterRegistry;
	private final ObjectMapper objectMapper;

	// 메시지 하나 처리 실패가 이 파드의 알림 수신 전체를 막으면 안 되므로, 파싱 실패든 뭐든 여기서 전부 삼킴
	// (컨테이너에도 ErrorHandler를 걸어뒀지만, 리스너 자체도 방어적으로 막아둠)
	@Override
	public void onMessage(Message message, byte[] pattern) {
		try {
			String payloadJson = new String(message.getBody());
			Map<?, ?> payload = objectMapper.readValue(payloadJson, Map.class);
			Long userId = Long.valueOf(String.valueOf(payload.get("userId")));
			sseEmitterRegistry.push(userId, payloadJson);
		} catch (Exception e) {
			log.warn("알림 broadcast 메시지 처리 실패: cause={}", e.getClass().getSimpleName());
		}
	}

}
