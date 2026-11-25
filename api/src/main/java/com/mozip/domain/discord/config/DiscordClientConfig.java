package com.mozip.domain.discord.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.mozip.domain.discord.service.DiscordClient;
import com.mozip.domain.discord.service.LoggingDiscordClient;
import com.mozip.domain.discord.service.RestDiscordClient;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

// discord.enabled가 false거나 봇 토큰, 채널 id가 비면 로그만 남기는 구현을 등록
@Slf4j
@Configuration
public class DiscordClientConfig {

	// 단체 생성 요청 스레드에서 커밋 후 호출하므로 디스코드가 느려도 오래 붙잡지 않게 짧게 잡음
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
	private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

	@Bean
	public DiscordClient discordClient(DiscordProperties properties, ObjectMapper objectMapper) {
		if (!properties.canSend()) {
			log.info("디스코드 알림 미사용: enabled={} (봇 토큰이나 채널 id가 비어도 미사용)", properties.enabled());
			return new LoggingDiscordClient();
		}
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
		requestFactory.setReadTimeout(READ_TIMEOUT);
		return new RestDiscordClient(RestClient.builder().requestFactory(requestFactory), properties.apiBaseUrl(),
			properties.botToken(), objectMapper);
	}

}
