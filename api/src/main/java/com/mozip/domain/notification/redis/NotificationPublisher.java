package com.mozip.domain.notification.redis;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.mozip.domain.notification.event.NotificationCreatedEvent;
import com.mozip.global.config.graphql.LocalDateTimeScalar;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

// NotificationEventListener(AFTER_COMMIT)가 호출. 파드 전체에 broadcast하면 각 파드의 NotificationRedisListener가 받아
// 자기 로컬 SSE 연결(SseEmitterRegistry)에만 전달함
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPublisher {

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;

	public void publish(NotificationCreatedEvent event) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("id", event.notificationId());
		payload.put("userId", event.userId());
		payload.put("type", event.type().name());
		payload.put("title", event.title());
		payload.put("linkUrl", event.linkUrl());
		payload.put("organizationId", event.organizationId());
		// GraphQL DateTime 스칼라(LocalDateTimeScalar)와 같은 형식(서울 오프셋 포함)으로 맞춤.
		// LocalDateTime.toString()만 쓰면 오프셋이 없어 notifications 쿼리 응답과 형식이 달라짐
		payload.put("createdAt", event.createdAt().atZone(LocalDateTimeScalar.ZONE)
			.toOffsetDateTime()
			.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
		try {
			redisTemplate.convertAndSend(NotificationChannel.EVENTS, objectMapper.writeValueAsString(payload));
		} catch (DataAccessException | JacksonException e) {
			// SSE는 best-effort라 발행 실패해도 알림 자체(DB)는 이미 저장돼 있음. 클라이언트는 notifications 쿼리로 보정
			log.warn("알림 broadcast 발행 실패: notificationId={}, cause={}", event.notificationId(),
				e.getClass().getSimpleName());
		}
	}

}
