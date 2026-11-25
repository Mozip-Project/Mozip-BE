package com.mozip.domain.mail.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.mozip.domain.mail.exception.MailErrorCode;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.redis.WorkerChannel;
import com.mozip.global.redis.WorkerTaskType;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

// 메일 발송을 worker에 비동기로 맡김(결정 006, Redis 리스너는 worker). B(결과 안내)도 이 메서드로 메일을 보냄
// 실제 SES 발송 성공 여부는 알 수 없고, 여기서는 작업이 worker로 전달됐는지만 보장함
@Component
@RequiredArgsConstructor
public class MailTaskPublisher {

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;

	// textBody는 HTML을 못 읽는 클라이언트용 대체 본문, 없으면 null
	public void publishSendMail(String to, String subject, String htmlBody, String textBody) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("type", WorkerTaskType.SEND_MAIL.name());
		payload.put("to", to);
		payload.put("subject", subject);
		payload.put("htmlBody", htmlBody);
		payload.put("textBody", textBody);
		try {
			redisTemplate.convertAndSend(WorkerChannel.TASKS, objectMapper.writeValueAsString(payload));
		} catch (DataAccessException | JacksonException e) {
			throw new BusinessException(MailErrorCode.SEND_FAILED).addContext("cause", e.getClass().getSimpleName());
		}
	}

}
