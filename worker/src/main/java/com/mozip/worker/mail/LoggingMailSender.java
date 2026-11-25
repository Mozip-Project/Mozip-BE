package com.mozip.worker.mail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

// mail.provider=ses가 아닌 환경(로컬, 테스트)의 기본 구현. 실제 발송 없이 로그만 남김
@Slf4j
@Component
@ConditionalOnMissingBean(MailSender.class)
public class LoggingMailSender implements MailSender {

	@Override
	public void send(String to, String subject, String htmlBody, String textBody) {
		log.info("[LoggingMailSender] to={}, subject={}", to, subject);
	}

}
