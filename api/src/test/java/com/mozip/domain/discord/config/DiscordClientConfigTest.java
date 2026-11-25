package com.mozip.domain.discord.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mozip.domain.discord.service.LoggingDiscordClient;
import com.mozip.domain.discord.service.RestDiscordClient;

import tools.jackson.databind.ObjectMapper;

// 설정에 따라 어떤 DiscordClient가 등록되는지, 누가 심사할 수 있는지 (Spring 없이)
class DiscordClientConfigTest {

	private final DiscordClientConfig config = new DiscordClientConfig();

	@Test
	@DisplayName("꺼져 있거나 봇 토큰, 채널 id가 비면 로그만 남기는 구현을 쓴다")
	void loggingClient() {
		assertThat(config.discordClient(properties(false, "token", "channel"), new ObjectMapper()))
			.isInstanceOf(LoggingDiscordClient.class);
		assertThat(config.discordClient(properties(true, "", "channel"), new ObjectMapper()))
			.isInstanceOf(LoggingDiscordClient.class);
		assertThat(config.discordClient(properties(true, "token", null), new ObjectMapper()))
			.isInstanceOf(LoggingDiscordClient.class);
	}

	@Test
	@DisplayName("켜져 있고 봇 토큰과 채널 id가 있으면 실제 호출 구현을 쓴다")
	void restClient() {
		assertThat(config.discordClient(properties(true, "token", "channel"), new ObjectMapper()))
			.isInstanceOf(RestDiscordClient.class);
	}

	@Test
	@DisplayName("허용된 역할이나 사용자 id가 하나라도 맞으면 심사할 수 있고, 목록이 비면 아무도 못 한다")
	void reviewer() {
		DiscordProperties properties = new DiscordProperties(true, "token", null, "channel",
			List.of(" 111 ", "222"), List.of("999", ""), "https://discord.test");

		assertThat(properties.isReviewer("1", List.of("222", "333"))).isTrue();
		assertThat(properties.isReviewer("999", List.of())).isTrue();
		assertThat(properties.isReviewer("1", List.of("333"))).isFalse();
		assertThat(properties.isReviewer(null, List.of())).isFalse();
		assertThat(properties.isReviewer("", List.of(""))).isFalse();

		DiscordProperties empty = properties(true, "token", "channel");
		assertThat(empty.isReviewer("999", List.of("111"))).isFalse();
	}

	private static DiscordProperties properties(boolean enabled, String botToken, String channelId) {
		return new DiscordProperties(enabled, botToken, null, channelId, List.of(), List.of(), "https://discord.test");
	}

}
