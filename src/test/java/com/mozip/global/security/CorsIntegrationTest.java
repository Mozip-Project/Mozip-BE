package com.mozip.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.mozip.TestcontainersConfiguration;
import com.mozip.global.logging.TraceIdFilter;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "auth.cors.allowed-origins=https://allowed.test")
@AutoConfigureMockMvc
class CorsIntegrationTest {

	private static final String ALLOWED_ORIGIN = "https://allowed.test";

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("허용된 Origin의 preflight는 쿠키 포함 요청을 허용한다")
	void allowedOrigin() throws Exception {
		mockMvc.perform(preflight(ALLOWED_ORIGIN).header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
				HttpHeaders.CONTENT_TYPE + "," + TraceIdFilter.TRACE_ID_HEADER))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
	}

	@Test
	@DisplayName("허용되지 않은 Origin의 preflight는 403으로 거부한다")
	void disallowedOrigin() throws Exception {
		mockMvc.perform(preflight("https://evil.test"))
			.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("인증 우회가 꺼져 있으면 X-Mock-Role 헤더는 CORS 허용 목록에 없다")
	void mockRoleHeaderNotAllowed() throws Exception {
		mockMvc.perform(preflight(ALLOWED_ORIGIN).header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-Mock-Role"))
			.andExpect(status().isForbidden());
	}

	private static MockHttpServletRequestBuilder preflight(String origin) {
		return options("/auth/login")
			.header(HttpHeaders.ORIGIN, origin)
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST");
	}

}
