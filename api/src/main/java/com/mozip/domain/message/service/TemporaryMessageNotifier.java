package com.mozip.domain.message.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.mozip.domain.application.entity.Application;
import com.mozip.domain.message.entity.Message;

import lombok.extern.slf4j.Slf4j;

// TODO(A 알림 #48): NotificationService가 들어오면 지원자마다 Notification(DOC_RESULT, message 연결)을 만드는 구현으로 교체.
// 지금은 로그만 남김 (결정 005)
@Slf4j
@Service
public class TemporaryMessageNotifier implements MessageNotifier {

	@Override
	public void notifyResult(Message message, List<Application> applications) {
		log.info("[message] messageId={} MOZIP 알림 {}건 생성(임시 구현, 알림 미생성)", message.getId(),
			applications.size());
	}

}
