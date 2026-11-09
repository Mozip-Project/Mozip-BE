package com.mozip.global.security.cookie;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

import com.mozip.global.security.jwt.JwtProperties;

class AuthCookieManagerTest {

	private static final JwtProperties JWT_PROPERTIES = new JwtProperties("secret", Duration.ofMinutes(30),
		Duration.ofDays(14));

	@Test
	@DisplayName("secure 설정이 켜져 있으면 Secure 속성을 붙이고, 꺼져 있으면(로컬 HTTP) 붙이지 않는다")
	void secureAttribute() {
		assertThat(setCookies(manager(true))).allMatch(cookie -> cookie.contains("; Secure"));
		assertThat(setCookies(manager(false))).noneMatch(cookie -> cookie.contains("; Secure"));
	}

	@Test
	@DisplayName("토큰 쿠키는 항상 HttpOnly, SameSite=Lax이고 리프레시 토큰은 /auth 경로에만 전송된다")
	void cookieAttributes() {
		List<String> cookies = setCookies(manager(true));

		assertThat(cookies).allMatch(cookie -> cookie.contains("; HttpOnly") && cookie.contains("; SameSite=Lax"));
		assertThat(cookies).anyMatch(cookie -> cookie.startsWith(AuthCookieManager.ACCESS_TOKEN_COOKIE + "=access")
			&& cookie.contains("; Path=/;"));
		assertThat(cookies).anyMatch(cookie -> cookie.startsWith(AuthCookieManager.REFRESH_TOKEN_COOKIE + "=refresh")
			&& cookie.contains("; Path=/auth"));
	}

	@Test
	@DisplayName("쿠키 삭제는 같은 경로로 Max-Age=0 쿠키를 덮어쓴다")
	void expire() {
		MockHttpServletResponse response = new MockHttpServletResponse();

		manager(true).expireTokenCookies(response);

		assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
			.hasSize(2)
			.allMatch(cookie -> cookie.contains("; Max-Age=0"));
	}

	private static List<String> setCookies(AuthCookieManager manager) {
		MockHttpServletResponse response = new MockHttpServletResponse();
		manager.addTokenCookies(response, "access", "refresh");
		return response.getHeaders(HttpHeaders.SET_COOKIE);
	}

	private static AuthCookieManager manager(boolean secure) {
		return new AuthCookieManager(new AuthCookieProperties(secure, CookieSameSite.LAX, null), JWT_PROPERTIES);
	}

}
