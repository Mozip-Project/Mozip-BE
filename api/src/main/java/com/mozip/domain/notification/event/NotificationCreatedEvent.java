package com.mozip.domain.notification.event;

import java.time.LocalDateTime;

import com.mozip.domain.notification.entity.NotificationType;

// 알림 저장 트랜잭션이 커밋된 뒤 Redis로 내보낼 이벤트 (NotificationEventListener가 소비)
public record NotificationCreatedEvent(
	Long notificationId,
	Long userId,
	NotificationType type,
	String title,
	String linkUrl,
	Long organizationId,
	LocalDateTime createdAt
) {
}
