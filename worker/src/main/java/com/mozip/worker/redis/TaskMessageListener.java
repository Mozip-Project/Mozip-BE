package com.mozip.worker.redis;

import java.util.Map;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import com.mozip.domain.school.repository.SchoolEmailVerificationCodeRepository;
import com.mozip.global.redis.WorkerTaskType;
import com.mozip.worker.mail.MailSender;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

// mozip:worker:tasks 채널(pub/sub)로 들어오는 학교 인증 메일만 처리. 일반 메일(SEND_MAIL)은
// MailTaskStream(Redis Stream)으로 옮겨서 MailTaskStreamListener가 처리함
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskMessageListener implements MessageListener {

	private final MailSender mailSender;
	private final SchoolEmailVerificationCodeRepository codeRepository;
	private final ObjectMapper objectMapper;

	// 메시지 하나 처리 실패가 이 리스너의 다른 메시지 수신을 막으면 안 되므로 전부 삼킴(컨테이너 ErrorHandler도 걸려 있음)
	@Override
	public void onMessage(Message message, byte[] pattern) {
		try {
			Map<?, ?> payload = objectMapper.readValue(message.getBody(), Map.class);
			WorkerTaskType type = WorkerTaskType.valueOf(String.valueOf(payload.get("type")));
			if (type == WorkerTaskType.SCHOOL_VERIFICATION_MAIL) {
				handleSchoolVerificationMail(payload);
			}
		} catch (Exception e) {
			log.warn("워커 작업 메시지 처리 실패: cause={}", e.getClass().getSimpleName());
		}
	}

	// api가 requestSchoolEmailVerification에서 markPending으로 남긴 상태를 실제 발송 결과로 덮어씀
	private void handleSchoolVerificationMail(Map<?, ?> payload) {
		String to = String.valueOf(payload.get("to"));
		String subject = String.valueOf(payload.get("subject"));
		String body = String.valueOf(payload.get("body"));
		try {
			mailSender.send(to, subject, body);
			codeRepository.markSent(to);
		} catch (RuntimeException e) {
			// 발송 실패. 받지도 못할 인증번호를 기다리게 두지 않고 바로 지워서 재요청할 수 있게 함
			codeRepository.markFailed(to);
			codeRepository.deleteCode(to);
			throw e;
		}
	}

}
