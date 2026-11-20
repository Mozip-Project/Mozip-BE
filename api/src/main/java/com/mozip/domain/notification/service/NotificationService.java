package com.mozip.domain.notification.service;

import java.util.Map;

import com.mozip.domain.notification.entity.NotificationType;

// B 흐름(초대, 대표자 인수, 결과 안내)이 알림을 보낼 때 쓰는 연결 함수. 실제 저장과 푸시는 A의 알림 이슈(#48)에서 구현
public interface NotificationService {

	// payload는 알림 제목과 이동 링크를 만들 재료 (organizationId, invitationId 등)
	void send(Long userId, NotificationType type, Map<String, Object> payload);

}
