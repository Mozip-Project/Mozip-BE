package com.mozip.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.mozip.TestcontainersConfiguration;
import com.mozip.global.security.cookie.AuthCookieManager;
import com.mozip.global.security.jwt.JwtProvider;

import jakarta.servlet.http.Cookie;

@Import({TestcontainersConfiguration.class, AdminOnlyTestController.class})
@SpringBootTest
@AutoConfigureMockMvc
class MethodSecurityIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtProvider jwtProvider;

	@Test
	@DisplayName("인증 우회가 꺼져 있으면 토큰 없는 요청은 401로 막는다")
	void unauthenticated() throws Exception {
		mockMvc.perform(get(AdminOnlyTestController.PATH))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("COMMON_401"));
	}

	@Test
	@DisplayName("@PreAuthorize 권한이 없으면 403, 있으면 통과한다")
	void preAuthorize() throws Exception {
		mockMvc.perform(get(AdminOnlyTestController.PATH).cookie(accessTokenCookie(Authority.USER)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("COMMON_403"));

		mockMvc.perform(get(AdminOnlyTestController.PATH).cookie(accessTokenCookie(Authority.ADMIN)))
			.andExpect(status().isOk());
	}

	private Cookie accessTokenCookie(Authority authority) {
		return new Cookie(AuthCookieManager.ACCESS_TOKEN_COOKIE, jwtProvider.createAccessToken(1L, authority));
	}

}
