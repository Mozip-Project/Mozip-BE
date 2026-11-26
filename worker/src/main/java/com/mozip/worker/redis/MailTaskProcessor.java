package com.mozip.worker.redis;

import java.util.Map;

import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.mozip.global.redis.MailTaskStream;
import com.mozip.worker.mail.MailSender;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// MailTaskStreamListener(최초 처리)와 MailTaskRetrySweeper(재시도)가 같이 쓰는 발송+ACK 로직
@Slf4j
@Component
@RequiredArgsConstructor
class MailTaskProcessor {

	private final MailSender mailSender;
	private final StringRedisTemplate redisTemplate;

	// 성공하면 ACK하고 true. 실패하면 ACK 안 하고 false (pending에 남아 재시도 대상이 됨)
	boolean process(MapRecord<String, String, String> record) {
		Map<String, String> value = record.getValue();
		try {
			mailSender.send(value.get("to"), value.get("subject"), value.get("body"));
			redisTemplate.opsForStream().acknowledge(MailTaskStream.CONSUMER_GROUP, record);
			return true;
		} catch (RuntimeException e) {
			log.warn("메일 작업 처리 실패: id={}, to={}, cause={}", record.getId(), value.get("to"), e.getClass().getSimpleName());
			return false;
		}
	}

}
