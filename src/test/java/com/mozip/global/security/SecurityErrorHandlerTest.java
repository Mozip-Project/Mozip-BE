package com.mozip.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import com.mozip.global.logging.TraceIdFilter;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class SecurityErrorHandlerTest {

	private final JsonMapper jsonMapper = JsonMapper.builder().build();
	private final SecurityErrorHandler securityErrorHandler = new SecurityErrorHandler(jsonMapper);

	@AfterEach
	void clearMdc() {
		MDC.clear();
	}

	@Test
	@DisplayName("인증이 없으면 401을 ApiResponse 형식으로 응답한다")
	void commence() throws Exception {
		MDC.put(TraceIdFilter.TRACE_ID_KEY, "test-trace-id");
		MockHttpServletResponse response = new MockHttpServletResponse();

		securityErrorHandler.commence(new MockHttpServletRequest(), response,
			new InsufficientAuthenticationException("인증 없음"));

		JsonNode body = jsonMapper.readTree(response.getContentAsString());
		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
		assertThat(body.get("success").asBoolean()).isFalse();
		assertThat(body.get("code").asString()).isEqualTo("COMMON_401");
		assertThat(body.get("message").asString()).isEqualTo("인증이 필요합니다.");
		assertThat(body.get("traceId").asString()).isEqualTo("test-trace-id");
	}

	@Test
	@DisplayName("권한이 없으면 403을 ApiResponse 형식으로 응답한다")
	void handle() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		securityErrorHandler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("권한 없음"));

		JsonNode body = jsonMapper.readTree(response.getContentAsString());
		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(body.get("code").asString()).isEqualTo("COMMON_403");
		assertThat(body.get("message").asString()).isEqualTo("접근 권한이 없습니다.");
	}

}
