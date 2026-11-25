package com.mozip.domain.discord.service;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;

// 디스코드 설정이 없는 환경(로컬, 테스트)용, 보내지 않고 로그만 남김
@Slf4j
public class LoggingDiscordClient implements DiscordClient {

	@Override
	public void createMessage(String channelId, Map<String, Object> payload) {
		log.info("디스코드 미사용 환경이라 메시지를 보내지 않음: channelId={}, content={}", channelId, payload.get("content"));
	}

}
