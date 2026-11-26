package com.mozip.worker.mail;

import lombok.extern.slf4j.Slf4j;

// mail.provider=ses가 아닌 환경(로컬, 테스트)의 기본 구현. 실제 발송 없이 로그만 남김
// @Component에 @ConditionalOnMissingBean(MailSender.class)를 걸면 자기 자신이 이미 등록된 MailSender로 잡혀
// 조건이 항상 거짓이 되므로(자기참조), 빈 등록은 MailSenderConfig의 @Bean 메서드로 옮김
@Slf4j
class LoggingMailSender implements MailSender {

	@Override
	public void send(String to, String subject, String htmlBody, String textBody) {
		log.info("[LoggingMailSender] to={}, subject={}", to, subject);
	}

}
