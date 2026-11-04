package com.mozip.domain.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import com.mozip.TestcontainersConfiguration;
import com.mozip.domain.auth.repository.RefreshTokenRepository;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.security.cookie.AuthCookieManager;
import com.mozip.global.security.jwt.TokenErrorCode;

import jakarta.servlet.http.Cookie;

// Redis 장애(AUTH_006)가 토큰 소비 전에 났는지 후에 났는지에 따라 쿠키 처리가 달라지는지 검증
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthReissueStoreFailureTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoSpyBean
	private RefreshTokenRepository refreshTokenRepository;

	@Test
	@DisplayName("토큰을 확인하기 전에 Redis가 실패하면 503으로 응답하되 쿠키는 유지해 재시도할 수 있게 한다")
	void failureBeforeConsumeKeepsCookies() throws Exception {
		Cookie refresh = login();
		doThrow(new BusinessException(TokenErrorCode.TOKEN_STORE_UNAVAILABLE))
			.when(refreshTokenRepository).consume(anyString());

		mockMvc.perform(post("/auth/reissue").cookie(refresh))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("AUTH_006"))
			.andExpect(cookie().doesNotExist(AuthCookieManager.ACCESS_TOKEN_COOKIE))
			.andExpect(cookie().doesNotExist(AuthCookieManager.REFRESH_TOKEN_COOKIE));
	}

	@Test
	@DisplayName("토큰을 폐기한 뒤 새 토큰 저장에 실패하면 503으로 응답하고 쿠키를 지운다 (같은 쿠키로는 복구 불가)")
	void failureAfterConsumeExpiresCookies() throws Exception {
		Cookie refresh = login();
		doThrow(new BusinessException(TokenErrorCode.TOKEN_STORE_UNAVAILABLE))
			.when(refreshTokenRepository).save(anyString(), any(), any());

		mockMvc.perform(post("/auth/reissue").cookie(refresh))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("AUTH_006"))
			.andExpect(cookie().maxAge(AuthCookieManager.ACCESS_TOKEN_COOKIE, 0))
			.andExpect(cookie().maxAge(AuthCookieManager.REFRESH_TOKEN_COOKIE, 0));
	}

	private Cookie login() throws Exception {
		String phone = "010" + ThreadLocalRandom.current().nextInt(10_000_000, 100_000_000);
		mockMvc.perform(post("/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"phone": "%s", "password": "mozip1234", "name": "김모집", "termsAgreed": true, "marketingAgreed": false}
					""".formatted(phone)))
			.andExpect(status().isCreated());
		return mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"phone": "%s", "password": "mozip1234"}
					""".formatted(phone)))
			.andExpect(status().isOk())
			.andReturn().getResponse().getCookie(AuthCookieManager.REFRESH_TOKEN_COOKIE);
	}

}
