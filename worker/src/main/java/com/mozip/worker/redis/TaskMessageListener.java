package com.mozip.worker.redis;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

// mozip:worker:tasks 채널로 들어오는 메시지 처리 (현재는 수신 로그만, 실제 처리 로직은 추후 추가)
@Slf4j
@Component
public class TaskMessageListener implements MessageListener {

	@Override
	public void onMessage(Message message, byte[] pattern) {
		log.info("[{}] received: {}", WorkerChannel.TASKS, new String(message.getBody()));
	}

}
