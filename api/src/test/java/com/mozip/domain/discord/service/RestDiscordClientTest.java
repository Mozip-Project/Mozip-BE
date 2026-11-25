package com.mozip.domain.discord.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.ObjectMapper;

// 실제 디스코드 대신 MockRestServiceServer로 요청 형식만 검증
class RestDiscordClientTest {

	private final RestClient.Builder builder = RestClient.builder();
	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
	private final RestDiscordClient client = new RestDiscordClient(builder, "https://discord.test/api/v10",
		"test-token", new ObjectMapper());

	@Test
	@DisplayName("채널 메시지 생성 API를 봇 토큰 헤더와 UTF-8 JSON body로 호출한다")
	void createMessage() {
		server.expect(requestTo("https://discord.test/api/v10/channels/12345/messages"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bot test-token"))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.content").value("단체 등록 심사 요청"))
			.andExpect(jsonPath("$.components[0].custom_id").value("org-review:approve:1"))
			.andRespond(withSuccess("{\"id\":\"1\"}", MediaType.APPLICATION_JSON));

		client.createMessage("12345", Map.of(
			"content", "단체 등록 심사 요청",
			"components", List.of(Map.of("custom_id", "org-review:approve:1"))));

		server.verify();
	}

	@Test
	@DisplayName("디스코드가 5xx로 응답하면 예외를 던진다 (호출하는 리스너가 잡아서 로그만 남김)")
	void serverError() {
		server.expect(requestTo("https://discord.test/api/v10/channels/12345/messages"))
			.andRespond(withServerError());

		assertThatThrownBy(() -> client.createMessage("12345", Map.of("content", "x")))
			.isInstanceOf(RestClientException.class);
	}

}
