package com.mozip.domain.evaluation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.graphql.test.tester.HttpGraphQlTester;

import com.mozip.TestcontainersConfiguration;
import com.mozip.domain.application.entity.Application;
import com.mozip.domain.evaluation.DocEvaluationFixtures;
import com.mozip.domain.evaluation.entity.DocScore;
import com.mozip.domain.evaluation.entity.Opinion;
import com.mozip.domain.evaluation.service.PostingResultChecker;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.posting.entity.Position;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.entity.PostingStatus;
import com.mozip.domain.posting.entity.Question;
import com.mozip.domain.posting.service.PostingService;
import com.mozip.domain.user.entity.Authority;
import com.mozip.domain.user.entity.User;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.security.cookie.AuthCookieManager;
import com.mozip.global.security.jwt.JwtProvider;

// 서비스 테스트로는 못 보는 것: 스키마와 DTO 필드가 실제로 맞물리는지, 평가 목록의 중첩 필드가 풀리는지,
// 확정한 뒤에 A의 공고 단계 전환이 실제로 열리는지
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureHttpGraphQlTester
class DocEvaluationSummaryGraphqlControllerTest {

	private static final String SET_DOC_RESULT = """
		mutation($applicationId: ID!, $result: DocResult!) {
			setDocResult(applicationId: $applicationId, result: $result) {
				application { id docResult }
				docResult
				summary { passCount holdCount failCount pendingCount canConfirm }
			}
		}
		""";

	private static final String CONFIRM_DOC_RESULT = """
		mutation($postingId: ID!) {
			confirmDocResult(postingId: $postingId) {
				posting { id docResultConfirmedAt }
				summary { canConfirm docResultConfirmedAt }
			}
		}
		""";

	@Autowired
	private HttpGraphQlTester graphQlTester;

	@Autowired
	private BeanFactory beanFactory;

	@Autowired
	private JwtProvider jwtProvider;

	@Autowired
	private PostingResultChecker postingResultChecker;

	@Autowired
	private PostingService postingService;

	private DocEvaluationFixtures fixtures;
	private User operator;
	private Member representative;
	private Member staff;
	private Posting posting;
	private Position position;
	private Question question;
	private Application first;
	private Application second;

	@BeforeEach
	void setUp() {
		fixtures = new DocEvaluationFixtures(beanFactory);
		Organization organization = fixtures.organization();
		operator = fixtures.user("대표 운영진");
		representative = fixtures.member(organization, operator, MemberRole.REPRESENTATIVE);
		staff = fixtures.member(organization, fixtures.user("부원 운영진"), MemberRole.OPERATOR);
		posting = fixtures.posting(organization, true);
		position = fixtures.position(posting, "개발", 0);
		question = fixtures.question(posting, 0);
		first = fixtures.submitted(posting, position, "김하나");
		second = fixtures.submitted(posting, position, "이두리");
		fixtures.saved(first, representative, Opinion.PASS, scores(5));
		fixtures.saved(first, staff, Opinion.PASS, scores(4));
		fixtures.saved(second, representative, Opinion.FAIL, scores(2));
	}

	@Test
	@DisplayName("취합 조회, 결과 지정, 확정을 차례로 하면 isDocResultConfirmed가 true가 되고 공고가 다음 단계로 넘어간다")
	void summaryToConfirmFlow() {
		HttpGraphQlTester asOperator = asUser(operator);

		GraphQlTester.Response summary = asOperator.document("""
			query($postingId: ID!) {
				docEvaluationSummary(postingId: $postingId) {
					rows {
						application { id user { name } position { name } docResult }
						docResult
						rank
						averageScore
						opinionCounts { pass hold fail notEvaluated }
						evaluationComplete
					}
					totalCount
					tabCounts { all pass hold fail }
					positionCounts { position { id name } count }
					page
					totalPages
					summary {
						totalCount evaluatedCount allEvaluated passCount holdCount failCount pendingCount canConfirm
						docResultConfirmedAt
					}
				}
			}
			""")
			.variable("postingId", posting.getId())
			.execute();
		summary.path("docEvaluationSummary.rows[*].application.user.name").entityList(String.class)
			.containsExactly("김하나", "이두리");
		summary.path("docEvaluationSummary.rows[0].application.id").entity(String.class)
			.isEqualTo(String.valueOf(first.getId()));
		summary.path("docEvaluationSummary.rows[0].application.position.name").entity(String.class).isEqualTo("개발");
		summary.path("docEvaluationSummary.rows[0].docResult").entity(String.class).isEqualTo("PENDING");
		summary.path("docEvaluationSummary.rows[0].rank").entity(Integer.class).isEqualTo(1);
		summary.path("docEvaluationSummary.rows[0].averageScore").entity(Double.class).isEqualTo(4.5);
		summary.path("docEvaluationSummary.rows[0].opinionCounts.pass").entity(Integer.class).isEqualTo(2);
		summary.path("docEvaluationSummary.rows[0].evaluationComplete").entity(Boolean.class).isEqualTo(true);
		summary.path("docEvaluationSummary.rows[1].rank").valueIsNull();
		summary.path("docEvaluationSummary.rows[1].averageScore").entity(Double.class).isEqualTo(2.0);
		summary.path("docEvaluationSummary.rows[1].opinionCounts.notEvaluated").entity(Integer.class).isEqualTo(1);
		summary.path("docEvaluationSummary.totalCount").entity(Integer.class).isEqualTo(2);
		summary.path("docEvaluationSummary.tabCounts.all").entity(Integer.class).isEqualTo(2);
		summary.path("docEvaluationSummary.positionCounts[0].position.name").entity(String.class).isEqualTo("개발");
		summary.path("docEvaluationSummary.positionCounts[0].count").entity(Integer.class).isEqualTo(2);
		summary.path("docEvaluationSummary.page").entity(Integer.class).isEqualTo(1);
		summary.path("docEvaluationSummary.totalPages").entity(Integer.class).isEqualTo(1);
		summary.path("docEvaluationSummary.summary.evaluatedCount").entity(Integer.class).isEqualTo(1);
		summary.path("docEvaluationSummary.summary.allEvaluated").entity(Boolean.class).isEqualTo(false);
		summary.path("docEvaluationSummary.summary.canConfirm").entity(Boolean.class).isEqualTo(false);
		summary.path("docEvaluationSummary.summary.docResultConfirmedAt").valueIsNull();

		// 필터 인자가 enum, ID 목록으로 제대로 묶이는지
		asOperator.document("""
			query($postingId: ID!, $positionIds: [ID!]) {
				docEvaluationSummary(postingId: $postingId, tab: PASS, positionIds: $positionIds, keyword: "하나",
					sort: NAME_ASC, page: 1) {
					totalCount
					tabCounts { all }
				}
			}
			""")
			.variable("postingId", posting.getId())
			.variable("positionIds", List.of(position.getId()))
			.execute()
			.path("docEvaluationSummary.totalCount").entity(Integer.class).isEqualTo(0)
			.path("docEvaluationSummary.tabCounts.all").entity(Integer.class).isEqualTo(1);

		GraphQlTester.Response evaluations = asOperator.document("""
			query($applicationId: ID!) {
				docEvaluations(applicationId: $applicationId) {
					id
					application { id }
					member { role user { name } }
					opinion
					memo
					scores { question { id title } score }
					submittedAt
				}
			}
			""")
			.variable("applicationId", first.getId())
			.execute();
		evaluations.path("docEvaluations[*].member.user.name").entityList(String.class)
			.containsExactly("대표 운영진", "부원 운영진");
		evaluations.path("docEvaluations[0].application.id").entity(String.class)
			.isEqualTo(String.valueOf(first.getId()));
		evaluations.path("docEvaluations[0].member.role").entity(String.class).isEqualTo("REPRESENTATIVE");
		evaluations.path("docEvaluations[0].opinion").entity(String.class).isEqualTo("PASS");
		evaluations.path("docEvaluations[0].memo").entity(String.class).isEqualTo("PASS 의견 메모");
		evaluations.path("docEvaluations[0].scores[0].question.id").entity(String.class)
			.isEqualTo(String.valueOf(question.getId()));
		evaluations.path("docEvaluations[0].scores[0].question.title").entity(String.class).isEqualTo("문항 0");
		evaluations.path("docEvaluations[0].scores[0].score").entity(Integer.class).isEqualTo(5);
		evaluations.path("docEvaluations[1].scores[0].score").entity(Integer.class).isEqualTo(4);
		evaluations.path("docEvaluations[0].submittedAt").hasValue();

		// 한 명이 아직 저장하지 않은 지원자는 결과를 지정할 수 없음
		asOperator.document(SET_DOC_RESULT)
			.variable("applicationId", second.getId())
			.variable("result", "FAIL")
			.execute()
			.errors()
			.satisfy(errors -> assertThat(errors.getFirst().getExtensions())
				.containsEntry("code", "EVALUATION_409")
				.containsEntry("reason", "NOT_ALL_SAVED"));

		// 실제 결과는 docResult로 내려가고 application.docResult는 결과 발표 전이라 PENDING으로 가려짐
		asOperator.document(SET_DOC_RESULT)
			.variable("applicationId", first.getId())
			.variable("result", "PASS")
			.execute()
			.path("setDocResult.docResult").entity(String.class).isEqualTo("PASS")
			.path("setDocResult.application.docResult").entity(String.class).isEqualTo("PENDING")
			.path("setDocResult.summary.passCount").entity(Integer.class).isEqualTo(1)
			.path("setDocResult.summary.pendingCount").entity(Integer.class).isEqualTo(1)
			.path("setDocResult.summary.canConfirm").entity(Boolean.class).isEqualTo(false);

		asOperator.document(CONFIRM_DOC_RESULT)
			.variable("postingId", posting.getId())
			.execute()
			.errors()
			.satisfy(errors -> assertThat(errors.getFirst().getExtensions())
				.containsEntry("code", "EVALUATION_422")
				.containsEntry("reason", "NOT_ALL_EVALUATED"));

		fixtures.saved(second, staff, Opinion.HOLD, scores(3));
		asOperator.document(SET_DOC_RESULT)
			.variable("applicationId", second.getId())
			.variable("result", "HOLD")
			.execute()
			.path("setDocResult.summary.holdCount").entity(Integer.class).isEqualTo(1)
			.path("setDocResult.summary.canConfirm").entity(Boolean.class).isEqualTo(false);
		asOperator.document(CONFIRM_DOC_RESULT)
			.variable("postingId", posting.getId())
			.execute()
			.errors()
			.satisfy(errors -> assertThat(errors.getFirst().getExtensions())
				.containsEntry("code", "EVALUATION_422")
				.containsEntry("reason", "HOLD_REMAINING"));

		asOperator.document(SET_DOC_RESULT)
			.variable("applicationId", second.getId())
			.variable("result", "FAIL")
			.execute()
			.path("setDocResult.docResult").entity(String.class).isEqualTo("FAIL")
			.path("setDocResult.summary.failCount").entity(Integer.class).isEqualTo(1)
			.path("setDocResult.summary.canConfirm").entity(Boolean.class).isEqualTo(true);

		// 확정 전에는 A의 단계 전환이 막혀 있음
		assertThat(postingResultChecker.isDocResultConfirmed(posting.getId())).isFalse();
		assertThatThrownBy(() -> postingService.advanceStatus(operator.getId(), posting.getId(),
			PostingStatus.DOC_RESULT_NOTICE)).isInstanceOf(BusinessException.class);

		asOperator.document(CONFIRM_DOC_RESULT)
			.variable("postingId", posting.getId())
			.execute()
			.path("confirmDocResult.posting.id").entity(String.class).isEqualTo(String.valueOf(posting.getId()))
			.path("confirmDocResult.posting.docResultConfirmedAt").hasValue()
			.path("confirmDocResult.summary.docResultConfirmedAt").hasValue()
			.path("confirmDocResult.summary.canConfirm").entity(Boolean.class).isEqualTo(false);

		assertThat(postingResultChecker.isDocResultConfirmed(posting.getId())).isTrue();

		// 확정 후에는 결과 변경도 다시 확정도 안 됨
		asOperator.document(SET_DOC_RESULT)
			.variable("applicationId", second.getId())
			.variable("result", "PASS")
			.execute()
			.errors()
			.satisfy(errors -> assertThat(errors.getFirst().getExtensions()).containsEntry("code", "EVALUATION_423"));
		asOperator.document(CONFIRM_DOC_RESULT)
			.variable("postingId", posting.getId())
			.execute()
			.errors()
			.satisfy(errors -> assertThat(errors.getFirst().getExtensions()).containsEntry("code", "EVALUATION_423"));

		assertThat(postingService.advanceStatus(operator.getId(), posting.getId(), PostingStatus.DOC_RESULT_NOTICE)
			.getStatus()).isEqualTo(PostingStatus.DOC_RESULT_NOTICE);
		// 결과 발표 단계가 되면 지정했던 결과가 application.docResult로도 보임
		asOperator.document("""
			query($postingId: ID!) {
				docEvaluationSummary(postingId: $postingId, tab: FAIL) { rows { docResult application { docResult } } }
			}
			""")
			.variable("postingId", posting.getId())
			.execute()
			.path("docEvaluationSummary.rows[0].docResult").entity(String.class).isEqualTo("FAIL")
			.path("docEvaluationSummary.rows[0].application.docResult").entity(String.class).isEqualTo("FAIL");
	}

	@Test
	@DisplayName("그 단체 운영진이 아니면 조회와 변경 모두 COMMON_403이다")
	void rejectsNonOperator() {
		HttpGraphQlTester asOutsider = asUser(fixtures.user("외부인"));

		asOutsider.document("""
			query($postingId: ID!) { docEvaluationSummary(postingId: $postingId) { totalCount } }
			""")
			.variable("postingId", posting.getId())
			.execute()
			.errors()
			.satisfy(errors -> assertThat(errors.getFirst().getExtensions()).containsEntry("code", "COMMON_403"));
		asOutsider.document("""
			query($applicationId: ID!) { docEvaluations(applicationId: $applicationId) { id } }
			""")
			.variable("applicationId", first.getId())
			.execute()
			.errors()
			.satisfy(errors -> assertThat(errors.getFirst().getExtensions()).containsEntry("code", "COMMON_403"));
		asOutsider.document(SET_DOC_RESULT)
			.variable("applicationId", first.getId())
			.variable("result", "PASS")
			.execute()
			.errors()
			.satisfy(errors -> assertThat(errors.getFirst().getExtensions()).containsEntry("code", "COMMON_403"));
		asOutsider.document(CONFIRM_DOC_RESULT)
			.variable("postingId", posting.getId())
			.execute()
			.errors()
			.satisfy(errors -> assertThat(errors.getFirst().getExtensions()).containsEntry("code", "COMMON_403"));
	}

	@Test
	@DisplayName("로그인 없이 부르면 COMMON_401이다")
	void requiresLogin() {
		graphQlTester.document("""
			query($postingId: ID!) { docEvaluationSummary(postingId: $postingId) { totalCount } }
			""")
			.variable("postingId", posting.getId())
			.execute()
			.errors()
			.satisfy(errors -> assertThat(errors.getFirst().getExtensions()).containsEntry("code", "COMMON_401"));
		graphQlTester.document(CONFIRM_DOC_RESULT)
			.variable("postingId", posting.getId())
			.execute()
			.errors()
			.satisfy(errors -> assertThat(errors.getFirst().getExtensions()).containsEntry("code", "COMMON_401"));
	}

	@Test
	@DisplayName("결과로 PENDING을 보내면 COMMON_400이고 없는 공고를 확정하면 POSTING_404다")
	void rejectsInvalidRequests() {
		HttpGraphQlTester asOperator = asUser(operator);

		asOperator.document(SET_DOC_RESULT)
			.variable("applicationId", first.getId())
			.variable("result", "PENDING")
			.execute()
			.errors()
			.satisfy(errors -> assertThat(errors.getFirst().getExtensions()).containsEntry("code", "COMMON_400"));
		asOperator.document(CONFIRM_DOC_RESULT)
			.variable("postingId", Long.MAX_VALUE)
			.execute()
			.errors()
			.satisfy(errors -> assertThat(errors.getFirst().getExtensions()).containsEntry("code", "POSTING_404"));
	}

	private List<DocScore> scores(int score) {
		return List.of(new DocScore(question.getId(), score));
	}

	private HttpGraphQlTester asUser(User user) {
		String accessToken = jwtProvider.createAccessToken(user.getId(), Authority.USER);
		return graphQlTester.mutate()
			.webTestClient(client -> client.defaultCookie(AuthCookieManager.ACCESS_TOKEN_COOKIE, accessToken))
			.build();
	}

}
