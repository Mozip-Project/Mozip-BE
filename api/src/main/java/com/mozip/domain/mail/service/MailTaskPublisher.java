package com.mozip.domain.mail.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.mozip.domain.mail.exception.MailErrorCode;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.redis.MailTaskStream;
import com.mozip.global.redis.WorkerChannel;
import com.mozip.global.redis.WorkerTaskType;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

// 메일 발송을 worker에 비동기로 맡김(결정 006, Redis 리스너는 worker). B(결과 안내)도 publishSendMail로 메일을 보냄
@Component
@RequiredArgsConstructor
public class MailTaskPublisher {

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;

	// 운영진이 보내는 일반 메일(결과 안내 등). MailTaskStream(Redis Stream)에 넣어 worker가 실패 시 최대 3번 재시도하고,
	// 다 소진하면 DLQ로 옮김. 여기서 보장하는 건 "스트림에 들어갔다"뿐이고 실제 발송 성공 여부는 worker가 결정함
	public void publishSendMail(String to, String subject, String body) {
		Map<String, String> fields = Map.of(
			"type", WorkerTaskType.SEND_MAIL.name(),
			"to", to,
			"subject", subject,
			"body", body);
		try {
			redisTemplate.opsForStream().add(StreamRecords.newRecord().ofMap(fields).withStreamKey(MailTaskStream.KEY));
		} catch (DataAccessException e) {
			throw new BusinessException(MailErrorCode.SEND_FAILED).addContext("cause", e.getClass().getSimpleName());
		}
	}

	// 학교 인증 메일 전용. 재시도는 없고(코드가 곧 만료돼 재요청이 쉬움), worker가 보낸 뒤
	// school:verify:status:{email}에 성공/실패를 남겨 schoolEmailVerificationStatus로 조회 가능
	public void publishSchoolVerificationMail(String to, String subject, String body) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("type", WorkerTaskType.SCHOOL_VERIFICATION_MAIL.name());
		payload.put("to", to);
		payload.put("subject", subject);
		payload.put("body", body);
		try {
			redisTemplate.convertAndSend(WorkerChannel.TASKS, objectMapper.writeValueAsString(payload));
		} catch (DataAccessException | JacksonException e) {
			throw new BusinessException(MailErrorCode.SEND_FAILED).addContext("cause", e.getClass().getSimpleName());
		}
	}

}
