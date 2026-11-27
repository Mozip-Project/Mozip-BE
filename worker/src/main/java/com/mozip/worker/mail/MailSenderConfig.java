package com.mozip.worker.mail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MailSenderConfig {

	// mail.provider=ses가 아니면(로컬, 테스트 기본값) 이 빈이 뜸. SesMailSender가 뜨면 그쪽이 우선
	@Bean
	@ConditionalOnMissingBean(MailSender.class)
	public MailSender loggingMailSender() {
		return new LoggingMailSender();
	}

}
