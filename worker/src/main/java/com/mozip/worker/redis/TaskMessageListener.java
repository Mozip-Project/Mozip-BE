package com.mozip.worker.redis;

import java.util.Map;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import com.mozip.global.redis.WorkerTaskType;
import com.mozip.worker.mail.MailSender;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

// mozip:worker:tasks 채널로 들어오는 메시지를 type 필드로 분기해서 처리
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskMessageListener implements MessageListener {

	private final MailSender mailSender;
	private final ObjectMapper objectMapper;

	// 메시지 하나 처리 실패가 이 리스너의 다른 메시지 수신을 막으면 안 되므로 전부 삼킴(컨테이너 ErrorHandler도 걸려 있음)
	@Override
	public void onMessage(Message message, byte[] pattern) {
		try {
			Map<?, ?> payload = objectMapper.readValue(message.getBody(), Map.class);
			WorkerTaskType type = WorkerTaskType.valueOf(String.valueOf(payload.get("type")));
			if (type == WorkerTaskType.SEND_MAIL) {
				handleSendMail(payload);
			}
		} catch (Exception e) {
			log.warn("워커 작업 메시지 처리 실패: cause={}", e.getClass().getSimpleName());
		}
	}

	private void handleSendMail(Map<?, ?> payload) {
		String to = String.valueOf(payload.get("to"));
		String subject = String.valueOf(payload.get("subject"));
		String htmlBody = String.valueOf(payload.get("htmlBody"));
		Object textBody = payload.get("textBody");
		mailSender.send(to, subject, htmlBody, textBody == null ? null : String.valueOf(textBody));
	}

}
