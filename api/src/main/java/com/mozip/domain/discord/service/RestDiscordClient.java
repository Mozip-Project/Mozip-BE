package com.mozip.domain.discord.service;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.ObjectMapper;

// 봇 토큰으로 디스코드 REST API 호출
public class RestDiscordClient implements DiscordClient {

	// 디스코드는 "DiscordBot (url, version)" 형식의 User-Agent를 요구
	private static final String USER_AGENT = "DiscordBot (https://mo-zip.com, 1.0)";

	private final RestClient restClient;
	private final ObjectMapper objectMapper;

	public RestDiscordClient(RestClient.Builder builder, String apiBaseUrl, String botToken,
		ObjectMapper objectMapper) {
		this.restClient = builder
			.baseUrl(apiBaseUrl)
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bot " + botToken)
			.defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
			.build();
		this.objectMapper = objectMapper;
	}

	// 한글이 깨지지 않게 UTF-8 바이트로 직접 직렬화해서 보냄
	@Override
	public void createMessage(String channelId, Map<String, Object> payload) {
		restClient.post()
			.uri("/channels/{channelId}/messages", channelId)
			.contentType(MediaType.APPLICATION_JSON)
			.body(objectMapper.writeValueAsBytes(payload))
			.retrieve()
			.toBodilessEntity();
	}

}
