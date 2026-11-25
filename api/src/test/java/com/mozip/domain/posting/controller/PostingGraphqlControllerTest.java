package com.mozip.domain.posting.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.HttpGraphQlTester;

import com.mozip.TestcontainersConfiguration;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.OrganizationScope;
import com.mozip.domain.organization.entity.OrganizationType;
import com.mozip.domain.organization.entity.Team;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.repository.OrganizationRepository;
import com.mozip.domain.organization.repository.TeamRepository;
import com.mozip.domain.posting.dto.PositionInput;
import com.mozip.domain.posting.dto.PostingDraftInput;
import com.mozip.domain.posting.dto.QuestionInput;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.entity.QuestionType;
import com.mozip.domain.posting.repository.PositionRepository;
import com.mozip.domain.posting.service.PostingService;
import com.mozip.domain.user.entity.Authority;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.security.cookie.AuthCookieManager;
import com.mozip.global.security.jwt.JwtProvider;

// Position.team, Question.position처럼 @BatchMapping을 추가한 자식-자식 연관이
// 실제 GraphQL 요청에서 에러 없이 해석되는지 확인 (서비스 테스트로는 리졸버 배선을 못 잡음)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureHttpGraphQlTester
class PostingGraphqlControllerTest {

	@Autowired
	private HttpGraphQlTester graphQlTester;

	@Autowired
	private PostingService postingService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private OrganizationRepository organizationRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private TeamRepository teamRepository;

	@Autowired
	private PositionRepository positionRepository;

	@Autowired
	private JwtProvider jwtProvider;

	private static final String SAVE_DRAFT = """
		mutation($organizationId: ID!, $input: PostingDraftInput!) {
			savePostingDraft(organizationId: $organizationId, input: $input) {
				id
			}
		}
		""";

	@Test
	@DisplayName("제목이 100자를 넘으면 COMMON_400과 fieldErrors(input.title)로 응답한다")
	void rejectsTitleOverMaxLength() {
		User operator = saveOperator();
		Organization organization = saveOrganization(operator);

		asUser(operator).document(SAVE_DRAFT)
			.variable("organizationId", organization.getId())
			.variable("input", Map.of("title", "a".repeat(101)))
			.execute()
			.errors()
			.satisfy(errors -> {
				assertThat(errors.getFirst().getExtensions()).containsEntry("code", "COMMON_400");
				assertThat(errors.getFirst().getExtensions().get("fieldErrors"))
					.asInstanceOf(InstanceOfAssertFactories.LIST)
					.singleElement()
					.hasFieldOrPropertyWithValue("field", "input.title");
			});
	}

	@Test
	@DisplayName("포지션 모집 인원이 0이면 COMMON_400과 fieldErrors(input.positions[0].headcount)로 응답한다")
	void rejectsNonPositiveHeadcount() {
		User operator = saveOperator();
		Organization organization = saveOrganization(operator);

		asUser(operator).document(SAVE_DRAFT)
			.variable("organizationId", organization.getId())
			.variable("input", Map.of("positions",
				List.of(Map.of("name", "백엔드", "headcount", 0, "sortOrder", 0))))
			.execute()
			.errors()
			.satisfy(errors -> {
				assertThat(errors.getFirst().getExtensions()).containsEntry("code", "COMMON_400");
				assertThat(errors.getFirst().getExtensions().get("fieldErrors"))
					.asInstanceOf(InstanceOfAssertFactories.LIST)
					.singleElement()
					.hasFieldOrPropertyWithValue("field", "input.positions[0].headcount");
			});
	}

	private User saveOperator() {
		String phone = "010" + ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999);
		return userRepository.save(User.create(phone, "encoded", "운영진", false, LocalDateTime.now()));
	}

	private Organization saveOrganization(User operator) {
		Organization organization = organizationRepository.save(
			Organization.create("검증 테스트 단체 " + ThreadLocalRandom.current().nextInt(1_000_000), OrganizationType.CLUB,
				null, OrganizationScope.CAMPUS, null, true, null, null));
		memberRepository.save(Member.create(organization, operator, null, MemberRole.REPRESENTATIVE));
		return organization;
	}

	private HttpGraphQlTester asUser(User user) {
		String accessToken = jwtProvider.createAccessToken(user.getId(), Authority.USER);
		return graphQlTester.mutate()
			.webTestClient(client -> client.defaultCookie(AuthCookieManager.ACCESS_TOKEN_COOKIE, accessToken))
			.build();
	}

	@Test
	@DisplayName("postings 상세에서 positions.team, questions.position을 같이 조회해도 배치 매핑으로 해석된다")
	void resolvesNestedPositionAndTeamThroughBatchMapping() {
		String phone = "010" + ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999);
		User operator = userRepository.save(User.create(phone, "encoded", "운영진", false, LocalDateTime.now()));
		Organization organization = organizationRepository.save(
			Organization.create("N+1 테스트 단체 " + ThreadLocalRandom.current().nextInt(1_000_000), OrganizationType.CLUB,
				null, OrganizationScope.CAMPUS, null, true, null, null));
		memberRepository.save(Member.create(organization, operator, null, MemberRole.REPRESENTATIVE));
		Team team = teamRepository.save(Team.create(organization, "백엔드팀"));

		Posting posting = postingService.saveDraft(operator.getId(), organization.getId(), null,
			basicInput("N+1 테스트 공고"));
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withPositions(List.of(new PositionInput(team.getId(), "백엔드", 1, 0))));
		Long positionId = positionRepository.findAllByPostingOrderBySortOrder(posting).get(0).getId();
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withQuestions(
				List.of(new QuestionInput(positionId, QuestionType.TEXT, "자기소개", null, true, 500, null, 0))));
		postingService.publish(operator.getId(), posting.getId());

		graphQlTester.document("""
			query($id: ID!) {
				posting(id: $id) {
					positions { id team { id name } }
					questions { id position { id } }
				}
			}
			""")
			.variable("id", posting.getId())
			.execute()
			.path("posting.positions[0].team.id").entity(String.class).isEqualTo(String.valueOf(team.getId()))
			.path("posting.positions[0].team.name").entity(String.class).isEqualTo("백엔드팀")
			.path("posting.questions[0].position.id").entity(String.class).isEqualTo(String.valueOf(positionId));
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
