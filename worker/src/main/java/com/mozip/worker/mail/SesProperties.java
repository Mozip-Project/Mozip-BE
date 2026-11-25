package com.mozip.worker.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// application.yml의 aws.ses.* 설정
@ConfigurationProperties("aws.ses")
public record SesProperties(
	@DefaultValue("ap-northeast-2") String region,
	String senderAddress,
	String senderName
) {
}
