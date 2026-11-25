package com.mozip.domain.notification.controller;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.mozip.global.security.AuthUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

// Swagger 문서용 어노테이션은 Controller 대신 ControllerApi 인터페이스에 작성
@Tag(name = "Notification", description = "알림 실시간 스트림 (SSE). 목록 조회/읽음 처리/설정 변경은 GraphQL notifications/markNotificationsRead/updateNotificationSettings")
public interface NotificationSseControllerApi {

	@Operation(summary = "알림 실시간 스트림", description = """
		notifications로 저장된 알림을 text/event-stream으로 실시간 전달합니다. event 이름은 notification 하나이며,
		data는 알림 JSON(id, userId, type, title, linkUrl, organizationId, createdAt)입니다.
		서버가 여러 파드로 떠 있어도 Redis Pub/Sub으로 파드 간 전달되어 이 연결에 도착합니다.
		DB가 기준이고 이 스트림은 best-effort이므로, 재연결 시 놓친 알림은 GraphQL notifications 쿼리로 보정하세요.""")
	SseEmitter stream(AuthUser authUser);

}
