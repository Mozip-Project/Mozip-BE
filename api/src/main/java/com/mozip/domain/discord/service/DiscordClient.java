package com.mozip.domain.discord.service;

import java.util.Map;

// 디스코드 REST API 호출 창구. 설정이 없거나 꺼져 있으면 LoggingDiscordClient가 쓰임 (DiscordClientConfig)
public interface DiscordClient {

	// 채널에 메시지 생성, payload는 디스코드 Create Message 요청 body 그대로
	void createMessage(String channelId, Map<String, Object> payload);

}
