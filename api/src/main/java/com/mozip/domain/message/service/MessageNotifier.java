package com.mozip.domain.message.service;

import java.util.List;

import com.mozip.domain.application.entity.Application;
import com.mozip.domain.message.entity.Message;

// 결과 안내의 MOZIP 알림 생성. A의 알림(#48)이 들어오면 TemporaryMessageNotifier를 실제 구현으로 교체 (결정 005)
public interface MessageNotifier {

	// 발송 시작 트랜잭션 안에서 불림. 실패하면 발송 시작 전체가 롤백됨
	void notifyResult(Message message, List<Application> applications);

}
