package com.mozip.domain.message.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.mozip.domain.message.entity.Message;
import com.mozip.domain.message.entity.MessageDelivery;

import lombok.extern.slf4j.Slf4j;

// TODO(B 결과 안내 worker): worker 채널(mozip:worker:tasks)에 메일 작업을 넣는 구현으로 교체.
// worker가 전달 내역을 SUCCESS/FAILED로 바꾸고 PENDING이 0이 되면 메시지를 SENT로 바꿈. 지금은 로그만 남김 (결정 005)
@Slf4j
@Service
public class TemporaryMessageMailQueue implements MessageMailQueue {

	@Override
	public void enqueue(Message message, List<MessageDelivery> deliveries) {
		log.info("[message] messageId={} 메일 작업 {}건 적재(임시 구현, 실제 발송 없음)", message.getId(),
			deliveries.size());
	}

}
