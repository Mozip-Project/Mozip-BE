package com.mozip.domain.message.service;

import java.util.List;

import com.mozip.domain.message.entity.Message;
import com.mozip.domain.message.entity.MessageDelivery;

// 결과 안내 메일 작업 적재. 결과 안내 worker가 들어오면 TemporaryMessageMailQueue를 실제 구현으로 교체 (결정 005)
public interface MessageMailQueue {

	// 발송 시작 트랜잭션 안에서 불림. 적재에 실패하면 발송 시작 전체가 롤백돼 메시지가 DRAFT로 남음
	void enqueue(Message message, List<MessageDelivery> deliveries);

}
