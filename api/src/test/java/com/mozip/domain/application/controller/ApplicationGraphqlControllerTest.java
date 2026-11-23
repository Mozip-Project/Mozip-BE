package com.mozip.domain.application.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.jdbc.core.JdbcTemplate;

import com.mozip.TestcontainersConfiguration;
import com.mozip.domain.application.dto.AnswerInput;
import com.mozip.domain.application.dto.ApplicationInput;
import com.mozip.domain.application.entity.Application;
import com.mozip.domain.application.service.ApplicationService;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.OrganizationScope;
import com.mozip.domain.organization.entity.OrganizationType;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.repository.OrganizationRepository;
import com.mozip.domain.posting.dto.PostingDraftInput;
import com.mozip.domain.posting.dto.QuestionInput;
import com.mozip.domain.posting.dto.QuestionOptionsInput;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.entity.PostingStatus;
import com.mozip.domain.posting.entity.QuestionType;
import com.mozip.domain.posting.repository.QuestionRepository;
import com.mozip.domain.posting.service.PostingService;
import com.mozip.domain.school.entity.School;
import com.mozip.domain.school.repository.SchoolRepository;
import com.mozip.domain.user.entity.Authority;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.security.cookie.AuthCookieManager;
import com.mozip.global.security.jwt.JwtProvider;

// 서비스 테스트로는 못 보는 두 가지: 인증 미들웨어가 로그인 안 한 요청을 막는지, docResult가 공고 상태에 따라 실제로 가려지는지
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureHttpGraphQlTester
class ApplicationGraphqlControllerTest {

	@Autowired
	private HttpGraphQlTester graphQlTester;

	@Autowired
	private PostingService postingService;

	@Autowired
	private ApplicationService applicationService;

	@Autowired
	private QuestionRepository questionRepository;

	@Autowired
	private OrganizationRepository organizationRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private SchoolRepository schoolRepository;

	@Autowired
	private JwtProvider jwtProvider;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("로그인 없이 saveApplicationDraft를 부르면 COMMON_401이다")
	void saveApplicationDraftRequiresLogin() {
		graphQlTester.document("""
			mutation($postingId: ID!, $input: ApplicationInput!) {
				saveApplicationDraft(postingId: $postingId, input: $input) { id }
			}
			""")
			.variable("postingId", "1")
			.variable("input", Map.of())
			.execute()
			.errors()
			.satisfy(errors -> assertThat(errors.getFirst().getExtensions()).containsEntry("code", "COMMON_401"));
	}

	@Test
	@DisplayName("docResult는 공고가 DOC_REVIEW일 때 DB 값이 PASS여도 PENDING으로 나오고, DOC_RESULT_NOTICE로 넘어가면 PASS로 나온다")
	void docResultHiddenUntilNotice() {
		User operator = saveUser("운영진");
		User applicant = saveUser("지원자");
		School school = schoolRepository.save(School.create("결과 테스트대학교 " + ThreadLocalRandom.current().nextInt(1_000_000), null));
		jdbcTemplate.update("update users set school_id = ? where id = ?", school.getId(), applicant.getId());
		Organization organization = organizationRepository.save(
			Organization.create("결과 테스트 단체 " + ThreadLocalRandom.current().nextInt(1_000_000), OrganizationType.CLUB,
				null, OrganizationScope.CAMPUS, null, true, null, null));
		memberRepository.save(Member.create(organization, operator, null, MemberRole.REPRESENTATIVE));

		Posting posting = postingService.saveDraft(operator.getId(), organization.getId(), null,
			basicInput("결과 공고 " + System.nanoTime()));
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(), withNoPosition());
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withQuestions(List.of(new QuestionInput(null, QuestionType.TEXT, "자기소개", null, true, 500, null, 0))));
		postingService.publish(operator.getId(), posting.getId());
		Long questionId = questionRepository.findAllByPostingOrderBySortOrder(posting).get(0).getId();

		Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(null, List.of(new AnswerInput(questionId, "답변", null)), null));
		applicationService.submit(applicant.getId(), application.getId());
		jdbcTemplate.update("update applications set doc_result = 'PASS' where id = ?", application.getId());
		postingService.advanceStatus(operator.getId(), posting.getId(), PostingStatus.DOC_REVIEW);

		HttpGraphQlTester asApplicant = asUser(applicant);
		String query = """
			query($id: ID!) { application(id: $id) { docResult } }
			""";
		asApplicant.document(query)
			.variable("id", application.getId())
			.execute()
			.path("application.docResult").entity(String.class).isEqualTo("PENDING");

		postingService.advanceStatus(operator.getId(), posting.getId(), PostingStatus.DOC_RESULT_NOTICE);

		asApplicant.document(query)
			.variable("id", application.getId())
			.execute()
			.path("application.docResult").entity(String.class).isEqualTo("PASS");
	}

	@Test
	@DisplayName("첨부 url이 500자를 넘으면 COMMON_400을 던진다 (DB 길이 제약에 걸려 COMMON_500이 되면 안 됨)")
	void rejectsOversizedAttachmentUrl() {
		User operator = saveUser("운영진");
		User applicant = saveUser("지원자");
		Organization organization = organizationRepository.save(
			Organization.create("검증 테스트 단체 " + ThreadLocalRandom.current().nextInt(1_000_000), OrganizationType.CLUB,
				null, OrganizationScope.CAMPUS, null, true, null, null));
		memberRepository.save(Member.create(organization, operator, null, MemberRole.REPRESENTATIVE));

		Posting posting = postingService.saveDraft(operator.getId(), organization.getId(), null,
			basicInput("검증 공고 " + System.nanoTime()));
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(), withNoPosition());
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withQuestions(List.of(new QuestionInput(null, QuestionType.FILE_LINK, "첨부", null, false, null,
				new QuestionOptionsInput(null, null, null, 5), 0))));
		postingService.publish(operator.getId(), posting.getId());
		Long questionId = questionRepository.findAllByPostingOrderBySortOrder(posting).get(0).getId();
		String tooLongUrl = "https://" + "a".repeat(500);

		asUser(applicant).document("""
			mutation($postingId: ID!, $input: ApplicationInput!) {
				saveApplicationDraft(postingId: $postingId, input: $input) { id }
			}
			""")
			.variable("postingId", posting.getId())
			.variable("input", Map.of(
				"answers", List.of(Map.of(
					"questionId", questionId,
					"attachments", List.of(Map.of("type", "LINK", "url", tooLongUrl))))))
			.execute()
			.errors()
			.satisfy(errors -> assertThat(errors.getFirst().getExtensions()).containsEntry("code", "COMMON_400"));
	}

	private PostingDraftInput basicInput(String title) {
		return new PostingDraftInput(title, "본문", "2026-2", "서울", "무료", true, false, null,
			null, null, LocalDateTime.now(), LocalDateTime.now().plusDays(7), null,
			null, null,
			null, null, null, null, null, null, null, null,
			null, null,
			null);
	}

	private PostingDraftInput withNoPosition() {
		return new PostingDraftInput(null, null, null, null, null, null, null, null,
			null, null, null, null, null,
			true, List.of(),
			null, null, null, null, null, null, null, null,
			null, null,
			null);
	}

	private PostingDraftInput withQuestions(List<QuestionInput> questions) {
		return new PostingDraftInput(null, null, null, null, null, null, null, null,
			null, null, null, null, null,
			null, null,
			null, null, null, null, null, null, null, null,
			true, questions,
			null);
	}

	private User saveUser(String name) {
		String phone = "0109" + String.format("%07d", ThreadLocalRandom.current().nextInt(10_000_000));
		return userRepository.save(User.create(phone, "encoded", name, false, LocalDateTime.now()));
	}

	private HttpGraphQlTester asUser(User user) {
		String accessToken = jwtProvider.createAccessToken(user.getId(), Authority.USER);
		return graphQlTester.mutate()
			.webTestClient(client -> client.defaultCookie(AuthCookieManager.ACCESS_TOKEN_COOKIE, accessToken))
			.build();
	}

}
