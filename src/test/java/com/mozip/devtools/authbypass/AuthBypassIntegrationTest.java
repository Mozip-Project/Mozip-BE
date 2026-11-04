package com.mozip.devtools.authbypass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.mozip.TestcontainersConfiguration;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.security.AdminOnlyTestController;
import com.mozip.global.security.Authority;
import com.mozip.global.security.cookie.AuthCookieManager;
import com.mozip.global.security.jwt.JwtProvider;

import jakarta.servlet.http.Cookie;

@Import({TestcontainersConfiguration.class, AdminOnlyTestController.class})
@SpringBootTest(properties = "auth.bypass.enabled=true")
@AutoConfigureMockMvc
class AuthBypassIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JwtProvider jwtProvider;

	@Test
	@DisplayName("인증 우회가 켜져 있으면 토큰 없이 @PreAuthorize가 달린 API도 호출할 수 있다")
	void bypassUrlAndMethodSecurity() throws Exception {
		mockMvc.perform(get(AdminOnlyTestController.PATH))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("앱 시작 시 역할별 개발용 회원이 만들어진다")
	void devUsersCreated() {
		for (MockRole role : MockRole.values()) {
			User user = userRepository.findByPhone(role.getPhone()).orElseThrow();
			assertThat(user.getAuthority()).isEqualTo(role.getAuthority());
			assertThat(user.getLastRole()).isEqualTo(role.getLastRole());
		}
	}

	@Test
	@DisplayName("개발용 회원은 비밀번호가 무작위라 로그인 API로는 로그인할 수 없다")
	void devUserCannotLogin() throws Exception {
		mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"phone": "%s", "password": "mozip1234"}
					""".formatted(MockRole.ADMIN.getPhone())))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_001"));
	}

	@Test
	@DisplayName("X-Mock-Role 헤더가 없으면 기본 역할(APPLICANT) 개발용 회원으로 인증된다")
	void defaultRole() throws Exception {
		Long applicantId = userId(MockRole.APPLICANT);

		mockMvc.perform(get(AdminOnlyTestController.ME_PATH))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.userId").value(applicantId))
			.andExpect(jsonPath("$.data.authority").value("USER"));
	}

	@Test
	@DisplayName("X-Mock-Role 헤더로 역할을 고르면 해당 개발용 회원으로 인증된다 (대소문자 무관)")
	void selectRole() throws Exception {
		mockMvc.perform(get(AdminOnlyTestController.ME_PATH).header(MockAuthenticationFilter.MOCK_ROLE_HEADER, "admin"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.userId").value(userId(MockRole.ADMIN)))
			.andExpect(jsonPath("$.data.authority").value("ADMIN"));

		mockMvc.perform(get(AdminOnlyTestController.ME_PATH)
				.header(MockAuthenticationFilter.MOCK_ROLE_HEADER, "OPERATOR"))
			.andExpect(jsonPath("$.data.userId").value(userId(MockRole.OPERATOR)));
	}

	@Test
	@DisplayName("X-Mock-Role 헤더 값이 잘못되면 400으로 응답한다")
	void invalidRole() throws Exception {
		mockMvc.perform(get(AdminOnlyTestController.ME_PATH).header(MockAuthenticationFilter.MOCK_ROLE_HEADER, "KING"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON_400"));
	}

	@Test
	@DisplayName("토큰이 있으면 X-Mock-Role 헤더보다 토큰 인증이 우선이다")
	void tokenWins() throws Exception {
		Cookie cookie = new Cookie(AuthCookieManager.ACCESS_TOKEN_COOKIE,
			jwtProvider.createAccessToken(999L, Authority.USER));

		mockMvc.perform(get(AdminOnlyTestController.ME_PATH).cookie(cookie)
				.header(MockAuthenticationFilter.MOCK_ROLE_HEADER, "ADMIN"))
			.andExpect(jsonPath("$.data.userId").value(999))
			.andExpect(jsonPath("$.data.authority").value("USER"));
	}

	private Long userId(MockRole role) {
		return userRepository.findByPhone(role.getPhone()).orElseThrow().getId();
	}

}
