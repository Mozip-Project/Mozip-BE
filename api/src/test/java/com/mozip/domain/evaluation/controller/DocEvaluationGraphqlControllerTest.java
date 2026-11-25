package com.mozip.domain.evaluation.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.jdbc.core.JdbcTemplate;

import com.mozip.TestcontainersConfiguration;
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
import com.mozip.domain.posting.dto.PositionInput;
import com.mozip.domain.posting.dto.PostingDraftInput;
import com.mozip.domain.posting.dto.QuestionInput;
import com.mozip.domain.posting.entity.Position;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.entity.Question;
import com.mozip.domain.posting.entity.QuestionType;
import com.mozip.domain.posting.repository.PositionRepository;
import com.mozip.domain.posting.repository.QuestionRepository;
import com.mozip.domain.posting.service.PostingService;
import com.mozip.domain.school.entity.School;
import com.mozip.domain.school.repository.SchoolRepository;
import com.mozip.domain.user.entity.Authority;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.security.cookie.AuthCookieManager;
import com.mozip.global.security.jwt.JwtProvider;

// 서비스 테스트로는 못 보는 것: 스키마와 Java 매핑이 맞는지, 트랜잭션 밖에서 하위 필드(지원자, 포지션, 평가자, 문항)가 풀리는지
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureHttpGraphQlTester
class DocEvaluationGraphqlControllerTest {

	private static final String LIST_QUERY = """
		query($postingId: ID!, $tab: DocEvaluationTab, $positionIds: [ID!], $keyword: String, $sort: ApplicationSort, $page: Int) {
			docEvaluationApplications(postingId: $postingId, tab: $tab, positionIds: $positionIds, keyword: $keyword, sort: $sort, page: $page) {
				rows {
					application { id status submittedAt user { name } position { name } }
					myEvaluationStatus
				}
				totalCount
				tabCounts { all notEvaluated evaluated }
				positionCounts { position { id name } count }
				page
				totalPages
			}
		}
		""";

	private static final String SAVE_MUTATION = """
		mutation($input: SaveDocEvaluationInput!, $submit: Boolean!) {
			saveDocEvaluation(input: $input, submit: $submit) {
				evaluation {
					id
					status
					opinion
					memo
					submittedAt
					updatedAt
					application { id user { name } }
					member { role user { name } organization { name } }
					scores { score question { id title position { name } } }
				}
				remainingCount
				nextUnevaluatedApplicationId
			}
		}
		""";

	private static final String MY_EVALUATION_QUERY = """
		query($applicationId: ID!) {
			myDocEvaluation(applicationId: $applicationId) {
				id status opinion memo submittedAt
				scores { score question { id title } }
			}
		}
		""";

	private static final String CHIPS_QUERY = """
		query($postingId: ID!, $tab: DocEvaluationTab) {
			docEvaluationApplicationChips(postingId: $postingId, tab: $tab) {
				application { id user { name } }
				myEvaluationStatus
			}
		}
		""";

	@Autowired
	private HttpGraphQlTester graphQlTester;

	@Autowired
	private PostingService postingService;

	@Autowired
	private ApplicationService applicationService;

	@Autowired
	private PositionRepository positionRepository;

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

	private User operator;
	private Organization organization;
	private School school;
	private Posting posting;
	private Position backend;
	private Position frontend;
	private Question commonQuestion;
	private Question backendQuestion;

	@BeforeEach
	void setUp() {
		operator = saveUser("평가 운영진");
		organization = organizationRepository.save(
			Organization.create("평가 테스트 단체 " + ThreadLocalRandom.current().nextInt(1_000_000),
				OrganizationType.CLUB, null, OrganizationScope.CAMPUS, null, true, null, null));
		memberRepository.save(Member.create(organization, operator, null, MemberRole.REPRESENTATIVE));
		school = schoolRepository.save(
			School.create("평가 테스트대학교 " + ThreadLocalRandom.current().nextInt(1_000_000), null));

		posting = postingService.saveDraft(operator.getId(), organization.getId(), null,
			basicInput("평가 공고 " + System.nanoTime()));
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withPositions(List.of(new PositionInput(null, "백엔드", 2, 0), new PositionInput(null, "프론트", 2, 1))));
		List<Position> positions = positionRepository.findAllByPostingOrderBySortOrder(posting);
		backend = positions.get(0);
		frontend = positions.get(1);
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(), withQuestions(List.of(
			new QuestionInput(null, QuestionType.TEXT, "지원 동기", null, false, 500, null, 0),
			new QuestionInput(backend.getId(), QuestionType.TEXT, "백엔드 경험", null, false, 500, null, 1))));
		List<Question> questions = questionRepository.findAllByPostingOrderBySortOrder(posting);
		commonQuestion = questions.get(0);
		backendQuestion = questions.get(1);
		postingService.publish(operator.getId(), posting.getId());
	}

	@Test
	@DisplayName("목록 조회, 자동 저장, 저장, 재조회가 이어지고 저장한 평가가 목록과 내 평가에 반영된다")
	void listAutoSaveSubmitAndReload() {
		Application first = submitApplication("김하나", backend);
		Application second = submitApplication("이두리", frontend);
		HttpGraphQlTester asOperator = asUser(operator);

		GraphQlTester.Response initial = asOperator.document(LIST_QUERY)
			.variable("postingId", posting.getId())
			.execute();
		initial.path("docEvaluationApplications.totalCount").entity(Integer.class).isEqualTo(2);
		initial.path("docEvaluationApplications.rows[*].application.id").entityList(String.class)
			.containsExactly(second.getId().toString(), first.getId().toString());
		initial.path("docEvaluationApplications.rows[*].application.user.name").entityList(String.class)
			.containsExactly("이두리", "김하나");
		initial.path("docEvaluationApplications.rows[*].application.position.name").entityList(String.class)
			.containsExactly("프론트", "백엔드");
		initial.path("docEvaluationApplications.rows[*].myEvaluationStatus").entityList(String.class)
			.containsExactly("NONE", "NONE");
		initial.path("docEvaluationApplications.tabCounts.notEvaluated").entity(Integer.class).isEqualTo(2);
		initial.path("docEvaluationApplications.tabCounts.evaluated").entity(Integer.class).isEqualTo(0);
		initial.path("docEvaluationApplications.positionCounts[*].position.name").entityList(String.class)
			.containsExactly("백엔드", "프론트");
		initial.path("docEvaluationApplications.positionCounts[*].count").entityList(Integer.class)
			.containsExactly(1, 1);
		initial.path("docEvaluationApplications.page").entity(Integer.class).isEqualTo(1);
		initial.path("docEvaluationApplications.totalPages").entity(Integer.class).isEqualTo(1);

		asOperator.document(MY_EVALUATION_QUERY)
			.variable("applicationId", first.getId())
			.execute()
			.path("myDocEvaluation").valueIsNull();

		GraphQlTester.Response autoSaved = asOperator.document(SAVE_MUTATION)
			.variable("input", Map.of("applicationId", first.getId(), "memo", "작성 중"))
			.variable("submit", false)
			.execute();
		autoSaved.path("saveDocEvaluation.evaluation.status").entity(String.class).isEqualTo("DRAFT");
		autoSaved.path("saveDocEvaluation.evaluation.opinion").valueIsNull();
		autoSaved.path("saveDocEvaluation.evaluation.submittedAt").valueIsNull();
		autoSaved.path("saveDocEvaluation.evaluation.scores").entityList(Object.class).hasSize(0);
		autoSaved.path("saveDocEvaluation.remainingCount").entity(Integer.class).isEqualTo(2);
		autoSaved.path("saveDocEvaluation.nextUnevaluatedApplicationId").entity(String.class)
			.isEqualTo(second.getId().toString());
		String evaluationId = autoSaved.path("saveDocEvaluation.evaluation.id").entity(String.class).get();

		asOperator.document(LIST_QUERY)
			.variable("postingId", posting.getId())
			.variable("tab", "NOT_EVALUATED")
			.execute()
			.path("docEvaluationApplications.rows[*].myEvaluationStatus").entityList(String.class)
			.containsExactly("NONE", "DRAFT");

		GraphQlTester.Response saved = asOperator.document(SAVE_MUTATION)
			.variable("input", Map.of(
				"applicationId", first.getId(),
				"opinion", "PASS",
				"memo", "좋음",
				"scores", List.of(
					Map.of("questionId", commonQuestion.getId(), "score", 4),
					Map.of("questionId", backendQuestion.getId(), "score", 5))))
			.variable("submit", true)
			.execute();
		saved.path("saveDocEvaluation.evaluation.id").entity(String.class).isEqualTo(evaluationId);
		saved.path("saveDocEvaluation.evaluation.status").entity(String.class).isEqualTo("SAVED");
		saved.path("saveDocEvaluation.evaluation.opinion").entity(String.class).isEqualTo("PASS");
		saved.path("saveDocEvaluation.evaluation.memo").entity(String.class).isEqualTo("좋음");
		saved.path("saveDocEvaluation.evaluation.submittedAt").entity(String.class)
			.satisfies(value -> assertThat(value).isNotBlank());
		saved.path("saveDocEvaluation.evaluation.updatedAt").entity(String.class)
			.satisfies(value -> assertThat(value).isNotBlank());
		saved.path("saveDocEvaluation.evaluation.application.user.name").entity(String.class).isEqualTo("김하나");
		saved.path("saveDocEvaluation.evaluation.member.role").entity(String.class).isEqualTo("REPRESENTATIVE");
		saved.path("saveDocEvaluation.evaluation.member.user.name").entity(String.class).isEqualTo("평가 운영진");
		saved.path("saveDocEvaluation.evaluation.member.organization.name").entity(String.class)
			.isEqualTo(organization.getName());
		saved.path("saveDocEvaluation.evaluation.scores[*].score").entityList(Integer.class).containsExactly(4, 5);
		saved.path("saveDocEvaluation.evaluation.scores[*].question.title").entityList(String.class)
			.containsExactly("지원 동기", "백엔드 경험");
		saved.path("saveDocEvaluation.evaluation.scores[1].question.position.name").entity(String.class)
			.isEqualTo("백엔드");
		saved.path("saveDocEvaluation.remainingCount").entity(Integer.class).isEqualTo(1);
		saved.path("saveDocEvaluation.nextUnevaluatedApplicationId").entity(String.class)
			.isEqualTo(second.getId().toString());

		GraphQlTester.Response reloaded = asOperator.document(MY_EVALUATION_QUERY)
			.variable("applicationId", first.getId())
			.execute();
		reloaded.path("myDocEvaluation.id").entity(String.class).isEqualTo(evaluationId);
		reloaded.path("myDocEvaluation.status").entity(String.class).isEqualTo("SAVED");
		reloaded.path("myDocEvaluation.opinion").entity(String.class).isEqualTo("PASS");
		reloaded.path("myDocEvaluation.scores[*].score").entityList(Integer.class).containsExactly(4, 5);
		reloaded.path("myDocEvaluation.scores[*].question.id").entityList(String.class)
			.containsExactly(commonQuestion.getId().toString(), backendQuestion.getId().toString());

		GraphQlTester.Response evaluatedTab = asOperator.document(LIST_QUERY)
			.variable("postingId", posting.getId())
			.variable("tab", "EVALUATED")
			.variable("positionIds", List.of(backend.getId()))
			.variable("keyword", "하나")
			.variable("sort", "NAME_ASC")
			.variable("page", 1)
			.execute();
		evaluatedTab.path("docEvaluationApplications.rows[*].application.id").entityList(String.class)
			.containsExactly(first.getId().toString());
		evaluatedTab.path("docEvaluationApplications.rows[0].myEvaluationStatus").entity(String.class)
			.isEqualTo("SAVED");
		evaluatedTab.path("docEvaluationApplications.tabCounts.all").entity(Integer.class).isEqualTo(1);
		evaluatedTab.path("docEvaluationApplications.tabCounts.evaluated").entity(Integer.class).isEqualTo(1);
		evaluatedTab.path("docEvaluationApplications.positionCounts[*].count").entityList(Integer.class)
			.containsExactly(1, 0);

		GraphQlTester.Response chips = asOperator.document(CHIPS_QUERY)
			.variable("postingId", posting.getId())
			.execute();
		chips.path("docEvaluationApplicationChips[*].application.user.name").entityList(String.class)
			.containsExactly("이두리", "김하나");
		chips.path("docEvaluationApplicationChips[*].myEvaluationStatus").entityList(String.class)
			.containsExactly("NONE", "SAVED");
	}

	@Test
	@DisplayName("로그인 없이 부르면 COMMON_401, 운영진이 아닌 회원이 부르면 COMMON_403이다")
	void requiresLoginAndOperator() {
		Application application = submitApplication("김하나", backend);
		User stranger = saveUser("남");

		graphQlTester.document(LIST_QUERY)
			.variable("postingId", posting.getId())
			.execute()
			.errors()
			.satisfy(errors -> assertThat(errors.getFirst().getExtensions()).containsEntry("code", "COMMON_401"));

		asUser(stranger).document(LIST_QUERY)
			.variable("postingId", posting.getId())
			.execute()
			.errors()
			.expect(e -> e.getErrorType() == ErrorType.FORBIDDEN && "COMMON_403".equals(e.getExtensions().get("code")));

		asUser(stranger).document(SAVE_MUTATION)
			.variable("input", Map.of("applicationId", application.getId()))
			.variable("submit", false)
			.execute()
			.errors()
			.expect(e -> e.getErrorType() == ErrorType.FORBIDDEN && "COMMON_403".equals(e.getExtensions().get("code")));
	}

	@Test
	@DisplayName("저장 입력이 틀리면 COMMON_400과 reason이, 없는 지원서면 EVALUATION_404가 응답에 담긴다")
	void saveErrorsCarryCodeAndReason() {
		Application application = submitApplication("김하나", backend);
		HttpGraphQlTester asOperator = asUser(operator);

		asOperator.document(SAVE_MUTATION)
			.variable("input", Map.of("applicationId", application.getId()))
			.variable("submit", true)
			.execute()
			.errors()
			.expect(e -> e.getErrorType() == ErrorType.BAD_REQUEST
				&& "COMMON_400".equals(e.getExtensions().get("code"))
				&& "OPINION_REQUIRED".equals(e.getExtensions().get("reason")));

		asOperator.document(SAVE_MUTATION)
			.variable("input", Map.of("applicationId", application.getId(), "opinion", "PASS",
				"scores", List.of(Map.of("questionId", commonQuestion.getId(), "score", 9))))
			.variable("submit", true)
			.execute()
			.errors()
			.expect(e -> "COMMON_400".equals(e.getExtensions().get("code"))
				&& "INVALID_SCORE".equals(e.getExtensions().get("reason")));

		asOperator.document(SAVE_MUTATION)
			.variable("input", Map.of("applicationId", -1))
			.variable("submit", false)
			.execute()
			.errors()
			.expect(e -> e.getErrorType() == ErrorType.NOT_FOUND
				&& "EVALUATION_404".equals(e.getExtensions().get("code")));

		asOperator.document(LIST_QUERY)
			.variable("postingId", posting.getId())
			.variable("page", 0)
			.execute()
			.errors()
			.expect(e -> e.getErrorType() == ErrorType.BAD_REQUEST
				&& "COMMON_400".equals(e.getExtensions().get("code")));
	}

	@Test
	@DisplayName("서류 결과가 확정된 공고의 평가를 저장하면 EVALUATION_423이고 목록은 그대로 조회된다")
	void saveAfterConfirmIsLocked() {
		Application application = submitApplication("김하나", backend);
		HttpGraphQlTester asOperator = asUser(operator);
		jdbcTemplate.update("update postings set doc_result_confirmed_at = now() where id = ?", posting.getId());

		Map<String, Object> input = new HashMap<>();
		input.put("applicationId", application.getId());
		input.put("opinion", "PASS");
		input.put("scores", List.of(
			Map.of("questionId", commonQuestion.getId(), "score", 3),
			Map.of("questionId", backendQuestion.getId(), "score", 3)));
		asOperator.document(SAVE_MUTATION)
			.variable("input", input)
			.variable("submit", true)
			.execute()
			.errors()
			.expect(e -> "EVALUATION_423".equals(e.getExtensions().get("code")));

		asOperator.document(LIST_QUERY)
			.variable("postingId", posting.getId())
			.execute()
			.path("docEvaluationApplications.totalCount").entity(Integer.class).isEqualTo(1);
	}

	private Application submitApplication(String name, Position position) {
		User applicant = saveUser(name);
		jdbcTemplate.update("update users set school_id = ? where id = ?", school.getId(), applicant.getId());
		Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(), null));
		return applicationService.submit(applicant.getId(), application.getId());
	}

	private User saveUser(String name) {
		String phone = "0108" + String.format("%07d", ThreadLocalRandom.current().nextInt(10_000_000));
		return userRepository.save(User.create(phone, "encoded", name, false, LocalDateTime.now()));
	}

	private HttpGraphQlTester asUser(User user) {
		String accessToken = jwtProvider.createAccessToken(user.getId(), Authority.USER);
		return graphQlTester.mutate()
			.webTestClient(client -> client.defaultCookie(AuthCookieManager.ACCESS_TOKEN_COOKIE, accessToken))
			.build();
	}

	private PostingDraftInput basicInput(String title) {
		return new PostingDraftInput(title, "본문", "2026-2", "서울", "무료", true, false, null,
			null, null, LocalDateTime.now(), LocalDateTime.now().plusDays(7), null,
			null, null,
			null, null, null, null, null, null, null, null,
			null, null,
			null);
	}

	private PostingDraftInput withPositions(List<PositionInput> positions) {
		return new PostingDraftInput(null, null, null, null, null, null, null, null,
			null, null, null, null, null,
			false, positions,
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

}
