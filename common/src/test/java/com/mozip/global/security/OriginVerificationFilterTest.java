package com.mozip.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.mozip.global.common.response.ApiResponseWriter;

import tools.jackson.databind.ObjectMapper;

class OriginVerificationFilterTest {

	private final OriginVerificationFilter filter = new OriginVerificationFilter(
		List.of("https://allowed.test", "http://localhost:3000"), new ApiResponseWriter(new ObjectMapper()));

	@Test
	@DisplayName("허용된 Origin의 POST는 통과한다")
	void allowedOrigin() throws Exception {
		assertThat(run(post(HttpHeaders.ORIGIN, "https://allowed.test"))).isTrue();
	}

	@Test
	@DisplayName("허용되지 않은 Origin의 POST는 COMMON_403으로 거부한다")
	void disallowedOrigin() throws Exception {
		MockHttpServletRequest request = post();
		request.addHeader(HttpHeaders.ORIGIN, "https://evil.test");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(chain.getRequest()).isNull();
		assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
		assertThat(response.getContentAsString()).contains("COMMON_403");
	}

	@Test
	@DisplayName("Origin이 없으면 Referer의 출처로 판단한다")
	void refererFallback() throws Exception {
		assertThat(run(post(HttpHeaders.REFERER, "https://allowed.test/login?next=/"))).isTrue();
		assertThat(run(post(HttpHeaders.REFERER, "https://evil.test/attack.html"))).isFalse();
	}

	@Test
	@DisplayName("Origin과 Referer가 모두 없는 요청(브라우저 외 클라이언트)은 통과한다")
	void noSourceHeaders() throws Exception {
		assertThat(run(post())).isTrue();
	}

	@Test
	@DisplayName("백엔드 자신의 주소에서 온 요청은 허용 목록에 없어도 통과한다 (Swagger UI, GraphiQL)")
	void sameOrigin() throws Exception {
		MockHttpServletRequest request = post();
		request.setScheme("http");
		request.setServerName("api.mozip.test");
		request.setServerPort(8080);
		request.addHeader(HttpHeaders.ORIGIN, "http://api.mozip.test:8080");

		assertThat(run(request)).isTrue();
	}

	@Test
	@DisplayName("기본 포트와 대소문자 차이는 같은 출처로 본다")
	void normalization() throws Exception {
		assertThat(run(post(HttpHeaders.ORIGIN, "HTTPS://Allowed.test:443"))).isTrue();
		assertThat(run(post(HttpHeaders.ORIGIN, "https://allowed.test:8443"))).isFalse();
	}

	@Test
	@DisplayName("GET, HEAD, OPTIONS는 출처와 상관없이 통과한다")
	void safeMethods() throws Exception {
		for (String method : List.of("GET", "HEAD", "OPTIONS")) {
			MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/test");
			request.addHeader(HttpHeaders.ORIGIN, "https://evil.test");
			assertThat(run(request)).as(method).isTrue();
		}
	}

	@Test
	@DisplayName("형식이 잘못된 Origin은 거부한다")
	void malformedOrigin() throws Exception {
		assertThat(run(post(HttpHeaders.ORIGIN, "null"))).isFalse();
		assertThat(run(post(HttpHeaders.ORIGIN, "not a url"))).isFalse();
	}

	private static MockHttpServletRequest post() {
		return new MockHttpServletRequest("POST", "/auth/login");
	}

	private static MockHttpServletRequest post(String headerName, String headerValue) {
		MockHttpServletRequest request = post();
		request.addHeader(headerName, headerValue);
		return request;
	}

	private boolean run(MockHttpServletRequest request) throws Exception {
		MockFilterChain chain = new MockFilterChain();
		filter.doFilter(request, new MockHttpServletResponse(), chain);
		return chain.getRequest() != null;
	}

}
