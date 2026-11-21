package com.mozip.domain.notification.service;

import java.util.Map;

import com.mozip.domain.notification.entity.NotificationType;

// B 흐름(초대, 대표자 인수, 결과 안내)이 알림을 보낼 때 쓰는 연결 함수
// TODO(A 알림): 실제 저장과 푸시 구현 (#48)
public interface NotificationService {

	// payload는 알림 제목과 이동 링크를 만들 값 (organizationId, invitationId 등)
	void send(Long userId, NotificationType type, Map<String, Object> payload);

}
