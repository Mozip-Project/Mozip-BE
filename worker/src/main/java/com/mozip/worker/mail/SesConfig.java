package com.mozip.worker.mail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

import lombok.RequiredArgsConstructor;

// mail.provider=ses일 때만 띄움. 로컬/테스트는 LoggingMailSender를 쓰므로 AWS 자격 증명이 없어도 기동됨
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "mail.provider", havingValue = "ses")
public class SesConfig {

	private final SesProperties sesProperties;

	@Bean
	public SesV2Client sesV2Client() {
		return SesV2Client.builder()
			.region(Region.of(sesProperties.region()))
			.credentialsProvider(DefaultCredentialsProvider.create())
			.build();
	}

}
