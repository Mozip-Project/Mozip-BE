package com.mozip.domain.notification.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.mozip.domain.notification.entity.NotificationType;

import lombok.extern.slf4j.Slf4j;

// TODO(A 알림): 알림 엔티티 저장과 알림함 노출로 교체, 지금은 로그만 남김
@Slf4j
@Service
public class LoggingNotificationService implements NotificationService {

	@Override
	public void send(Long userId, NotificationType type, Map<String, Object> payload) {
		// payload는 키만 남김
		log.info("Notification (not persisted yet): userId={}, type={}, payloadKeys={}", userId, type,
			payload.keySet());
	}

}
