package com.mozip.domain.notification.controller;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.HttpGraphQlTester;

import com.mozip.TestcontainersConfiguration;
import com.mozip.domain.notification.entity.NotificationType;
import com.mozip.domain.notification.service.NotificationService;
import com.mozip.domain.user.entity.Authority;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.security.cookie.AuthCookieManager;
import com.mozip.global.security.jwt.JwtProvider;

// 서비스 테스트로는 리졸버 배선(role nullable 처리)을 못 잡아서 실제 GraphQL 요청으로 확인
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureHttpGraphQlTester
class NotificationGraphqlControllerTest {

	@Autowired
	private HttpGraphQlTester graphQlTester;

	@Autowired
	private JwtProvider jwtProvider;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private NotificationService notificationService;

	private User user;

	@BeforeEach
	void setUp() {
		String phone = "010" + ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999);
		user = userRepository.save(User.create(phone, "encoded", "지원자", false, LocalDateTime.now()));
	}

	@Test
	@DisplayName("role 없이 notifications를 조회하면 지원자/운영진 알림이 같이 최신순으로 내려온다")
	void queriesNotificationsWithoutRole() {
		notificationService.send(user.getId(), NotificationType.DOC_RESULT, null, "지원자 알림", null);
		notificationService.send(user.getId(), NotificationType.EVALUATION_REQUEST, null, "운영진 알림", null);

		asUser(user).document("{ notifications(page: 0, size: 20) { title } }")
			.execute()
			.path("notifications")
			.entityList(Object.class)
			.hasSize(2);
	}

	@Test
	@DisplayName("각 알림의 unreadCount는 role 없이 섞여 있어도 자기 탭 기준으로 센다")
	void queriesUnreadCountPerNotification() {
		notificationService.send(user.getId(), NotificationType.DOC_RESULT, null, "지원자 알림 1", null);
		notificationService.send(user.getId(), NotificationType.FINAL_RESULT, null, "지원자 알림 2", null);
		notificationService.send(user.getId(), NotificationType.EVALUATION_REQUEST, null, "운영진 알림", null);

		asUser(user).document("""
			{
				notifications(page: 0, size: 20) {
					type
					unreadCount
				}
			}
			""")
			.execute()
			.path("notifications[?(@.type == 'EVALUATION_REQUEST')].unreadCount")
			.entityList(Integer.class)
			.containsExactly(1)
			.path("notifications[?(@.type == 'DOC_RESULT')].unreadCount")
			.entityList(Integer.class)
			.containsExactly(2);
	}

	private HttpGraphQlTester asUser(User user) {
		String accessToken = jwtProvider.createAccessToken(user.getId(), Authority.USER);
		// MockMvc는 Cookie 헤더 문자열을 파싱하지 않으므로 WebTestClient의 쿠키로 전달
		return graphQlTester.mutate()
			.webTestClient(client -> client.defaultCookie(AuthCookieManager.ACCESS_TOKEN_COOKIE, accessToken))
			.build();
	}

}
