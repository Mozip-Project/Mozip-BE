package com.mozip.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import com.mozip.TestcontainersConfiguration;
import com.mozip.domain.auth.repository.RefreshTokenRepository;
import com.mozip.global.security.cookie.AuthCookieManager;

import jakarta.servlet.http.Cookie;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

	private static final String PASSWORD = "mozip1234";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Test
	@DisplayName("회원가입 후 로그인하면 토큰을 body가 아닌 HttpOnly 쿠키로 발급한다")
	void signupAndLogin() throws Exception {
		String phone = randomPhone();

		mockMvc.perform(post("/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(signupJson(phone, true)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.userId").isNumber());

		mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginJson(phone, PASSWORD)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.name").value("김모집"))
			.andExpect(jsonPath("$.data.lastRole").value("APPLICANT"))
			.andExpect(jsonPath("$.data.accessToken").doesNotExist())
			.andExpect(cookie().httpOnly(AuthCookieManager.ACCESS_TOKEN_COOKIE, true))
			.andExpect(cookie().path(AuthCookieManager.ACCESS_TOKEN_COOKIE, "/"))
			.andExpect(cookie().sameSite(AuthCookieManager.ACCESS_TOKEN_COOKIE, "Lax"))
			.andExpect(cookie().httpOnly(AuthCookieManager.REFRESH_TOKEN_COOKIE, true))
			.andExpect(cookie().path(AuthCookieManager.REFRESH_TOKEN_COOKIE, "/auth"));
	}

	@Test
	@DisplayName("이미 가입된 휴대폰 번호로 가입하면 409로 응답한다")
	void duplicatePhone() throws Exception {
		String phone = signup();

		mockMvc.perform(post("/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(signupJson(phone, true)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("USER_001"));
	}

	@Test
	@DisplayName("필수 약관에 동의하지 않으면 400으로 응답한다")
	void termsNotAgreed() throws Exception {
		mockMvc.perform(post("/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(signupJson(randomPhone(), false)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON_400"));
	}

	@Test
	@DisplayName("비밀번호가 틀리거나 없는 번호면 같은 코드(AUTH_001)로 응답해 가입 여부를 드러내지 않는다")
	void invalidCredentials() throws Exception {
		String phone = signup();

		mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginJson(phone, "wrong-password1")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_001"))
			.andExpect(cookie().doesNotExist(AuthCookieManager.ACCESS_TOKEN_COOKIE));

		mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginJson(randomPhone(), PASSWORD)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_001"));
	}

	@Test
	@DisplayName("access_token 쿠키가 있으면 인증이 필요한 요청을 통과하고, 잘못된 토큰이면 AUTH_003으로 응답한다")
	void accessTokenCookie() throws Exception {
		MockHttpServletResponse login = login(signup());

		// 인증은 통과했고 해당 경로에 API가 없어 404
		mockMvc.perform(get("/api/protected").cookie(login.getCookie(AuthCookieManager.ACCESS_TOKEN_COOKIE)))
			.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/protected").cookie(new Cookie(AuthCookieManager.ACCESS_TOKEN_COOKIE, "forged")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_003"));
	}

	@Test
	@DisplayName("재발급하면 새 토큰 쿠키를 주고, 이미 사용한 리프레시 토큰은 다시 쓸 수 없다")
	void reissueRotatesRefreshToken() throws Exception {
		Cookie oldRefresh = login(signup()).getCookie(AuthCookieManager.REFRESH_TOKEN_COOKIE);

		MockHttpServletResponse reissued = mockMvc.perform(post("/auth/reissue").cookie(oldRefresh))
			.andExpect(status().isOk())
			.andExpect(cookie().httpOnly(AuthCookieManager.ACCESS_TOKEN_COOKIE, true))
			.andReturn().getResponse();
		assertThat(reissued.getCookie(AuthCookieManager.REFRESH_TOKEN_COOKIE).getValue())
			.isNotEqualTo(oldRefresh.getValue());

		clearReissueGrace();
		mockMvc.perform(post("/auth/reissue").cookie(oldRefresh))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_004"))
			.andExpect(cookie().maxAge(AuthCookieManager.ACCESS_TOKEN_COOKIE, 0))
			.andExpect(cookie().maxAge(AuthCookieManager.REFRESH_TOKEN_COOKIE, 0));
	}

	@Test
	@DisplayName("같은 리프레시 토큰으로 동시에 재발급하면 늦은 쪽은 AUTH_007을 받고 쿠키와 다른 세션은 유지된다")
	void duplicateReissueKeepsSessions() throws Exception {
		String phone = signup();
		Cookie deviceA = login(phone).getCookie(AuthCookieManager.REFRESH_TOKEN_COOKIE);
		Cookie deviceB = login(phone).getCookie(AuthCookieManager.REFRESH_TOKEN_COOKIE);
		Cookie deviceARotated = mockMvc.perform(post("/auth/reissue").cookie(deviceA))
			.andExpect(status().isOk())
			.andReturn().getResponse().getCookie(AuthCookieManager.REFRESH_TOKEN_COOKIE);

		// 먼저 처리된 요청이 발급한 쿠키를 지우지 않도록 Set-Cookie 없이 응답
		mockMvc.perform(post("/auth/reissue").cookie(deviceA))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("AUTH_007"))
			.andExpect(cookie().doesNotExist(AuthCookieManager.ACCESS_TOKEN_COOKIE))
			.andExpect(cookie().doesNotExist(AuthCookieManager.REFRESH_TOKEN_COOKIE));

		mockMvc.perform(post("/auth/reissue").cookie(deviceARotated))
			.andExpect(status().isOk());
		mockMvc.perform(post("/auth/reissue").cookie(deviceB))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("같은 리프레시 토큰으로 정말 동시에 재발급해도 하나만 성공하고 나머지는 AUTH_007이며 다른 세션은 유지된다")
	void concurrentReissue() throws Exception {
		String phone = signup();
		Cookie deviceA = login(phone).getCookie(AuthCookieManager.REFRESH_TOKEN_COOKIE);
		Cookie deviceB = login(phone).getCookie(AuthCookieManager.REFRESH_TOKEN_COOKIE);
		int requests = 4;
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Integer>> results = new ArrayList<>();
		try (ExecutorService executor = Executors.newFixedThreadPool(requests)) {
			for (int i = 0; i < requests; i++) {
				results.add(executor.submit(() -> {
					start.await();
					return mockMvc.perform(post("/auth/reissue").cookie(deviceA)).andReturn().getResponse().getStatus();
				}));
			}
			// 모든 스레드가 준비된 뒤 한 번에 출발시켜 소비 구간이 겹치게 함
			start.countDown();
		}
		List<Integer> statuses = new ArrayList<>();
		for (Future<Integer> result : results) {
			statuses.add(result.get());
		}

		assertThat(statuses).containsOnly(200, 409).containsOnlyOnce(200);
		mockMvc.perform(post("/auth/reissue").cookie(deviceB))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("이미 사용한 리프레시 토큰이 유예 시간 후 다시 들어오면 탈취로 보고 그 회원의 다른 기기 세션도 모두 끊는다")
	void reusedRefreshTokenRevokesAllSessions() throws Exception {
		String phone = signup();
		Cookie deviceA = login(phone).getCookie(AuthCookieManager.REFRESH_TOKEN_COOKIE);
		Cookie deviceB = login(phone).getCookie(AuthCookieManager.REFRESH_TOKEN_COOKIE);
		Cookie deviceARotated = mockMvc.perform(post("/auth/reissue").cookie(deviceA))
			.andExpect(status().isOk())
			.andReturn().getResponse().getCookie(AuthCookieManager.REFRESH_TOKEN_COOKIE);

		// 유예 시간이 지난 뒤 이미 폐기된 deviceA 토큰 재사용
		clearReissueGrace();
		mockMvc.perform(post("/auth/reissue").cookie(deviceA))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_004"));

		// 재사용 전까지 유효했던 토큰들도 모두 폐기됨
		mockMvc.perform(post("/auth/reissue").cookie(deviceARotated))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_004"));
		mockMvc.perform(post("/auth/reissue").cookie(deviceB))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_004"));
	}

	@Test
	@DisplayName("리프레시 토큰 쿠키 없이 재발급하면 AUTH_004로 응답한다")
	void reissueWithoutCookie() throws Exception {
		mockMvc.perform(post("/auth/reissue"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_004"));
	}

	@Test
	@DisplayName("로그아웃하면 쿠키를 지우고, 그 리프레시 토큰으로는 재발급할 수 없다")
	void logout() throws Exception {
		Cookie refresh = login(signup()).getCookie(AuthCookieManager.REFRESH_TOKEN_COOKIE);

		mockMvc.perform(post("/auth/logout").cookie(refresh))
			.andExpect(status().isOk())
			.andExpect(cookie().maxAge(AuthCookieManager.ACCESS_TOKEN_COOKIE, 0))
			.andExpect(cookie().maxAge(AuthCookieManager.REFRESH_TOKEN_COOKIE, 0));

		mockMvc.perform(post("/auth/reissue").cookie(refresh))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_004"));
	}

	@Test
	@DisplayName("비밀번호에 한글 등 ASCII 외 문자가 있으면 BCrypt까지 가지 않고 400으로 응답한다")
	void nonAsciiPassword() throws Exception {
		// 64자 이내지만 UTF-8로 77바이트라 BCrypt 72바이트 제한을 넘는 값
		String password = "a1" + "가".repeat(25);

		mockMvc.perform(post("/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(signupJson(randomPhone(), password, true)))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("로그인 비밀번호가 64자를 넘으면 400으로 응답한다")
	void tooLongLoginPassword() throws Exception {
		mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginJson(signup(), "a".repeat(65))))
			.andExpect(status().isBadRequest());
	}

	private String signup() throws Exception {
		String phone = randomPhone();
		mockMvc.perform(post("/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(signupJson(phone, true)))
			.andExpect(status().isCreated());
		return phone;
	}

	private MockHttpServletResponse login(String phone) throws Exception {
		return mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginJson(phone, PASSWORD)))
			.andExpect(status().isOk())
			.andReturn().getResponse();
	}

	// 소비 마커를 지워 유예 시간(CONSUMED_GRACE)이 지난 상태를 만듦
	private void clearReissueGrace() {
		redisTemplate.delete(redisTemplate.keys(RefreshTokenRepository.CONSUMED_KEY_PREFIX + "*"));
	}

	// 테스트끼리 DB를 공유해 번호를 무작위로 생성
	private static String randomPhone() {
		return "010" + ThreadLocalRandom.current().nextInt(10_000_000, 100_000_000);
	}

	private static String signupJson(String phone, boolean termsAgreed) {
		return signupJson(phone, PASSWORD, termsAgreed);
	}

	private static String signupJson(String phone, String password, boolean termsAgreed) {
		return """
			{"phone": "%s", "password": "%s", "name": "김모집", "termsAgreed": %s, "marketingAgreed": false}
			""".formatted(phone, password, termsAgreed);
	}

	private static String loginJson(String phone, String password) {
		return """
			{"phone": "%s", "password": "%s"}
			""".formatted(phone, password);
	}

}
