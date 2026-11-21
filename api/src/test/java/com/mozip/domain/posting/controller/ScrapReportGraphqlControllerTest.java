package com.mozip.domain.posting.controller;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.HttpGraphQlTester;

import com.mozip.TestcontainersConfiguration;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.OrganizationScope;
import com.mozip.domain.organization.entity.OrganizationType;
import com.mozip.domain.organization.repository.OrganizationRepository;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.repository.PostingRepository;
import com.mozip.domain.user.entity.Authority;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.security.cookie.AuthCookieManager;
import com.mozip.global.security.jwt.JwtProvider;

// 서비스 테스트로는 리졸버 배선(뮤테이션 인자, 에러 응답 형식)을 못 잡아서 실제 GraphQL 요청으로 확인
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureHttpGraphQlTester
class ScrapReportGraphqlControllerTest {

	@Autowired
	private HttpGraphQlTester graphQlTester;

	@Autowired
	private JwtProvider jwtProvider;

	@Autowired
	private OrganizationRepository organizationRepository;

	@Autowired
	private PostingRepository postingRepository;

	@Autowired
	private UserRepository userRepository;

	private Organization organization;
	private Posting posting;
	private User user;

	@BeforeEach
	void setUp() {
		organization = organizationRepository.save(
			Organization.create("테스트 단체", OrganizationType.CLUB, OrganizationScope.CAMPUS, null, false));
		posting = postingRepository.save(Posting.create(organization, "공고", false, false));
		String phone = "010" + ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999);
		user = userRepository.save(User.create(phone, "encoded", "지원자", false, LocalDateTime.now()));
	}

	@Test
	@DisplayName("toggleScrap을 두 번 호출하면 추가 후 해제되고, myScraps 목록도 같이 바뀐다")
	void togglesScrapThroughGraphql() {
		HttpGraphQlTester tester = asUser(user);

		tester.document("""
			mutation($postingId: ID!) {
				toggleScrap(postingId: $postingId) { scrapped scrapCount }
			}
			""")
			.variable("postingId", posting.getId())
			.execute()
			.path("toggleScrap.scrapped").entity(Boolean.class).isEqualTo(true)
			.path("toggleScrap.scrapCount").entity(Integer.class).isEqualTo(1);

		tester.document("{ myScraps(page: 0, size: 20) { totalCount items { id organization { id name } } } }")
			.execute()
			.path("myScraps.totalCount").entity(Integer.class).isEqualTo(1)
			.path("myScraps.items[0].id").entity(String.class).isEqualTo(String.valueOf(posting.getId()))
			.path("myScraps.items[0].organization.id").entity(String.class)
				.isEqualTo(String.valueOf(organization.getId()));

		tester.document("""
			mutation($postingId: ID!) {
				toggleScrap(postingId: $postingId) { scrapped scrapCount }
			}
			""")
			.variable("postingId", posting.getId())
			.execute()
			.path("toggleScrap.scrapped").entity(Boolean.class).isEqualTo(false)
			.path("toggleScrap.scrapCount").entity(Integer.class).isEqualTo(0);
	}

	@Test
	@DisplayName("reportPosting을 같은 공고에 두 번 호출하면 두 번째는 REPORT_409로 거부한다")
	void rejectsDuplicateReportThroughGraphql() {
		HttpGraphQlTester tester = asUser(user);
		String document = """
			mutation($postingId: ID!) {
				reportPosting(postingId: $postingId, reason: INAPPROPRIATE, detail: "부적절해요")
			}
			""";

		tester.document(document)
			.variable("postingId", posting.getId())
			.execute()
			.path("reportPosting").entity(Boolean.class).isEqualTo(true);

		tester.document(document)
			.variable("postingId", posting.getId())
			.execute()
			.errors()
			.satisfy(errors -> {
				assertThat(errors).hasSize(1);
				assertThat(errors.getFirst().getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
				assertThat(errors.getFirst().getExtensions()).containsEntry("code", "REPORT_409");
			});
	}

	private HttpGraphQlTester asUser(User user) {
		String accessToken = jwtProvider.createAccessToken(user.getId(), Authority.USER);
		// MockMvc는 Cookie 헤더 문자열을 파싱하지 않으므로 WebTestClient의 쿠키로 전달
		return graphQlTester.mutate()
			.webTestClient(client -> client.defaultCookie(AuthCookieManager.ACCESS_TOKEN_COOKIE, accessToken))
			.build();
	}

}
