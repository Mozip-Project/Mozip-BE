package com.mozip.devtools.authbypass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.TestcontainersConfiguration;
import com.mozip.domain.user.entity.Authority;
import com.mozip.devtools.seed.SeedFixtures;
import com.mozip.devtools.seed.SeedRunner;
import com.mozip.domain.application.entity.Application;
import com.mozip.domain.application.service.ApplicationService;
import com.mozip.domain.evaluation.dto.DocEvaluationApplicationPage;
import com.mozip.domain.evaluation.service.DocEvaluationService;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.entity.PostingStatus;
import com.mozip.domain.posting.repository.PostingRepository;
import com.mozip.domain.posting.repository.QuestionRepository;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.security.AdminOnlyTestController;
import com.mozip.global.security.cookie.AuthCookieManager;
import com.mozip.global.security.jwt.JwtProvider;

import jakarta.servlet.http.Cookie;

@Import({TestcontainersConfiguration.class, AdminOnlyTestController.class})
@SpringBootTest(properties = {"auth.bypass.enabled=true", "auth.cors.allowed-origins=https://allowed.test"})
@AutoConfigureMockMvc
class AuthBypassIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JwtProvider jwtProvider;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private PostingRepository postingRepository;

	@Autowired
	private QuestionRepository questionRepository;

	@Autowired
	private ApplicationService applicationService;

	@Autowired
	private DocEvaluationService docEvaluationService;

	@Autowired
	private SeedRunner seedRunner;

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
	@Transactional
	@DisplayName("앱 시작 시 개발용 운영진은 개발용 단체의 대표자로 등록된다")
	void devOperatorIsRepresentativeOfSeedOrganization() {
		List<Member> memberships = memberRepository.findAllByUserId(userId(MockRole.OPERATOR));

		assertThat(memberships).hasSize(1);
		assertThat(memberships.getFirst().getRole()).isEqualTo(MemberRole.REPRESENTATIVE);
		assertThat(memberships.getFirst().getOrganization().getName()).isEqualTo(SeedFixtures.ORGANIZATION_NAME);
	}

	@Test
	@Transactional
	@DisplayName("앱 시작 시 개발용 단체에 모집 중 공고 1개, 문항 3개, 제출된 지원서 10개가 만들어진다")
	void seedPostingWithSubmittedApplications() {
		List<Posting> postings = seedPostings();

		assertThat(postings).hasSize(1);
		Posting posting = postings.getFirst();
		assertThat(posting.getStatus()).isEqualTo(PostingStatus.RECRUITING);
		assertThat(questionRepository.findAllByPostingOrderBySortOrder(posting)).hasSize(3);
		List<Application> applications = applicationService.getSubmittedApplications(posting.getId());
		assertThat(applications).hasSize(10);
		assertThat(applications).extracting(application -> application.getUser().getName())
			.containsExactlyInAnyOrderElementsOf(SeedFixtures.APPLICANT_NAMES);

		// 개발용 운영진으로 서류 평가 목록을 바로 볼 수 있어야 함
		DocEvaluationApplicationPage page = docEvaluationService.findApplications(userId(MockRole.OPERATOR),
			posting.getId(), null, null, null, null, null);
		assertThat(page.totalCount()).isEqualTo(10);
		assertThat(page.tabCounts().notEvaluated()).isEqualTo(10);
	}

	@Test
	@Transactional
	@DisplayName("시드를 다시 실행해도 공고와 지원서가 더 쌓이지 않는다")
	void seedIsIdempotent() {
		seedRunner.run(new DefaultApplicationArguments());

		List<Posting> postings = seedPostings();
		assertThat(postings).hasSize(1);
		assertThat(applicationService.getSubmittedApplications(postings.getFirst().getId())).hasSize(10);
		assertThat(memberRepository.findAllByUserId(userId(MockRole.OPERATOR))).hasSize(1);
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
	@DisplayName("인증 우회 중에는 X-Mock-Role 헤더가 CORS 허용 목록에 추가된다")
	void mockRoleHeaderAllowedInCors() throws Exception {
		mockMvc.perform(options("/auth/login")
				.header(HttpHeaders.ORIGIN, "https://allowed.test")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, MockAuthenticationFilter.MOCK_ROLE_HEADER))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
				Matchers.containsString(MockAuthenticationFilter.MOCK_ROLE_HEADER)));
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

	private List<Posting> seedPostings() {
		Member membership = memberRepository.findAllByUserId(userId(MockRole.OPERATOR)).getFirst();
		return postingRepository
			.findAllByOrganizationAndStatusInOrderByCreatedAtDesc(membership.getOrganization(),
				List.of(PostingStatus.values()))
			.stream()
			.filter(posting -> SeedFixtures.POSTING_TITLE.equals(posting.getTitle()))
			.toList();
	}

	private Long userId(MockRole role) {
		return userRepository.findByPhone(role.getPhone()).orElseThrow().getId();
	}

}
