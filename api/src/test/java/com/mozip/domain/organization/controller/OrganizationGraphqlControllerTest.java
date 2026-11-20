package com.mozip.domain.organization.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.HttpGraphQlTester;

import com.mozip.TestcontainersConfiguration;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.repository.OrganizationRepository;
import com.mozip.domain.school.entity.School;
import com.mozip.domain.school.repository.SchoolRepository;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.domain.user.entity.Authority;
import com.mozip.global.security.cookie.AuthCookieManager;
import com.mozip.global.security.jwt.JwtProvider;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureHttpGraphQlTester
class OrganizationGraphqlControllerTest {

	private static final String CREATE = """
		mutation($input: CreateOrganizationInput!) {
			createOrganization(input: $input) {
				id name type councilLevel scope noTeam reviewStatus proofLink proofFileUrl
				school { id name }
				members { role user { id name } team { id } }
				teams { id }
			}
		}
		""";

	private static final String UPDATE = """
		mutation($id: ID!, $input: UpdateOrganizationInput!) {
			updateOrganization(id: $id, input: $input) {
				id tagline logoUrl keywords fields
				links { type url }
			}
		}
		""";

	@Autowired
	private HttpGraphQlTester graphQlTester;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private SchoolRepository schoolRepository;

	@Autowired
	private OrganizationRepository organizationRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private JwtProvider jwtProvider;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private static final String ME = """
		{
			me {
				id name lastRole
				lastOrganization { id name }
				memberships { role organization { id name school { name } } team { id } }
			}
		}
		""";

	private static final String MEMBERS = """
		query($organizationId: ID!) {
			members(organizationId: $organizationId) {
				id role user { id name } team { id } createdAt
			}
		}
		""";

	private static final String REMOVE = """
		mutation($memberId: ID!) {
			removeMember(memberId: $memberId)
		}
		""";

	private static final String SELECT = """
		mutation($organizationId: ID!) {
			selectOrganization(organizationId: $organizationId) {
				lastRole
				lastOrganization { id name }
			}
		}
		""";

	@Test
	@DisplayName("단체를 만들면 me의 memberships에 나오고, 심사 전 상세는 소속 운영진만 볼 수 있다")
	void createThenListThenDetail() {
		User user = saveUser();
		School school = schoolRepository.save(School.create("테스트대학교", null));
		HttpGraphQlTester asUser = asUser(user);

		String organizationId = asUser.document(CREATE)
			.variable("input", Map.of(
				"name", "총학생회",
				"type", "STUDENT_COUNCIL",
				"councilLevel", "COLLEGE",
				"scope", "CAMPUS",
				"schoolId", school.getId().toString(),
				"proofLink", "https://example.com/proof"))
			.execute()
			.path("createOrganization.reviewStatus").entity(String.class).isEqualTo("PENDING")
			.path("createOrganization.noTeam").entity(Boolean.class).isEqualTo(false)
			.path("createOrganization.school.name").entity(String.class).isEqualTo("테스트대학교")
			.path("createOrganization.members[0].role").entity(String.class).isEqualTo("REPRESENTATIVE")
			.path("createOrganization.members[0].user.id").entity(String.class).isEqualTo(user.getId().toString())
			.path("createOrganization.members[0].team").valueIsNull()
			.path("createOrganization.teams").entityList(Object.class).hasSize(0)
			.path("createOrganization.id").entity(String.class).get();

		asUser.document(ME)
			.execute()
			.path("me.id").entity(String.class).isEqualTo(user.getId().toString())
			.path("me.lastRole").entity(String.class).isEqualTo("APPLICANT")
			.path("me.lastOrganization").valueIsNull()
			.path("me.memberships[*].organization.id").entityList(String.class).containsExactly(organizationId)
			.path("me.memberships[0].role").entity(String.class).isEqualTo("REPRESENTATIVE")
			.path("me.memberships[0].organization.school.name").entity(String.class).isEqualTo("테스트대학교")
			.path("me.memberships[0].team").valueIsNull();

		asUser.document("query($id: ID!) { organization(id: $id) { name councilLevel school { name } proofLink } }")
			.variable("id", organizationId)
			.execute()
			.path("organization.name").entity(String.class).isEqualTo("총학생회")
			.path("organization.councilLevel").entity(String.class).isEqualTo("COLLEGE")
			.path("organization.school.name").entity(String.class).isEqualTo("테스트대학교")
			.path("organization.proofLink").entity(String.class).isEqualTo("https://example.com/proof");

		graphQlTester.document("query($id: ID!) { organization(id: $id) { id } }")
			.variable("id", organizationId)
			.execute()
			.errors()
			.satisfy(errors -> assertThat(errors.getFirst().getExtensions()).containsEntry("code", "ORG_404"));
	}

	@Test
	@DisplayName("승인된 단체 상세는 로그인 없이 볼 수 있지만 증빙은 null이고 운영진 명단은 COMMON_403이다")
	void anonymousSeesPublicFieldsOnly() {
		User user = saveUser();
		String organizationId = createOrganization(asUser(user));
		approve(organizationId);

		graphQlTester.document("query($id: ID!) { organization(id: $id) { name proofLink proofFileUrl } }")
			.variable("id", organizationId)
			.execute()
			.path("organization.name").entity(String.class).isEqualTo("동아리")
			.path("organization.proofLink").valueIsNull()
			.path("organization.proofFileUrl").valueIsNull();

		graphQlTester.document("query($id: ID!) { organization(id: $id) { name members { user { phone } } } }")
			.variable("id", organizationId)
			.execute()
			.errors()
			.satisfy(errors -> assertThat(errors.getFirst().getExtensions()).containsEntry("code", "COMMON_403"));

		asUser(saveUser()).document("query($id: ID!) { organization(id: $id) { members { id } } }")
			.variable("id", organizationId)
			.execute()
			.errors()
			.satisfy(errors -> assertThat(errors.getFirst().getExtensions()).containsEntry("code", "COMMON_403"));
	}

	@Test
	@DisplayName("운영진 명단과 팀에서 단체, 학교까지 중첩해 조회해도 LAZY 예외 없이 응답한다")
	void nestedFieldsResolve() {
		User user = saveUser();
		School school = schoolRepository.save(School.create("중첩대학교", null));
		jdbcTemplate.update("update users set school_id = ? where id = ?", school.getId(), user.getId());
		HttpGraphQlTester asUser = asUser(user);
		String organizationId = createOrganization(asUser);
		jdbcTemplate.update("insert into teams (organization_id, name, created_at, updated_at) values (?, '기획', now(), now())",
			Long.valueOf(organizationId));

		asUser.document("""
				query($id: ID!) {
					organization(id: $id) {
						members { organization { name school { name } } user { school { name } } team { id } }
						teams { name organization { name school { name } } }
					}
				}
				""")
			.variable("id", organizationId)
			.execute()
			.errors().verify()
			.path("organization.members[0].organization.name").entity(String.class).isEqualTo("동아리")
			.path("organization.members[0].user.school.name").entity(String.class).isEqualTo("중첩대학교")
			.path("organization.teams[0].organization.name").entity(String.class).isEqualTo("동아리");
	}

	@Test
	@DisplayName("운영진이 프로필을 수정하면 승인된 단체 상세에 키워드/분야/링크가 나오고, 다른 회원은 COMMON_403이다")
	void updateThenDetail() {
		User user = saveUser();
		HttpGraphQlTester asUser = asUser(user);
		String organizationId = asUser.document(CREATE)
			.variable("input", Map.of("name", "모집 동아리", "type", "CLUB", "scope", "UNION"))
			.execute()
			.path("createOrganization.id").entity(String.class).get();
		Map<String, Object> input = Map.of(
			"tagline", "함께 만드는 모집 플랫폼",
			"fields", List.of("IT_DEV", "DESIGN_MEDIA"),
			"keywords", List.of("개발", "디자인"),
			"links", List.of("instagram.com/mozip", "https://mozip.kr"));

		asUser.document(UPDATE)
			.variable("id", organizationId)
			.variable("input", input)
			.execute()
			.path("updateOrganization.tagline").entity(String.class).isEqualTo("함께 만드는 모집 플랫폼")
			.path("updateOrganization.logoUrl").valueIsNull()
			.path("updateOrganization.keywords").entityList(String.class).containsExactly("개발", "디자인")
			.path("updateOrganization.fields").entityList(String.class).containsExactly("IT_DEV", "DESIGN_MEDIA")
			.path("updateOrganization.links[0].type").entity(String.class).isEqualTo("INSTAGRAM")
			.path("updateOrganization.links[0].url").entity(String.class).isEqualTo("https://instagram.com/mozip")
			.path("updateOrganization.links[1].type").entity(String.class).isEqualTo("WEBSITE");

		// 승인된 단체여야 로그인 없이 상세와 자동완성에 나옴
		approve(organizationId);
		graphQlTester.document("query($id: ID!) { organization(id: $id) { tagline keywords } }")
			.variable("id", organizationId)
			.execute()
			.path("organization.tagline").entity(String.class).isEqualTo("함께 만드는 모집 플랫폼")
			.path("organization.keywords").entityList(String.class).containsExactly("개발", "디자인");

		asUser.document("query($prefix: String!) { keywordSuggestions(prefix: $prefix) }")
			.variable("prefix", "디")
			.execute()
			.path("keywordSuggestions").entityList(String.class).contains("디자인");

		asUser(saveUser()).document(UPDATE)
			.variable("id", organizationId)
			.variable("input", input)
			.execute()
			.errors()
			.satisfy(errors -> {
				assertThat(errors.getFirst().getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
				assertThat(errors.getFirst().getExtensions()).containsEntry("code", "COMMON_403");
			});
	}

	@Test
	@DisplayName("키워드가 10개를 넘으면 COMMON_400과 피그마 문구의 fieldErrors로 응답한다")
	void tooManyKeywords() {
		User user = saveUser();
		HttpGraphQlTester asUser = asUser(user);
		String organizationId = asUser.document(CREATE)
			.variable("input", Map.of("name", "모집 동아리", "type", "CLUB", "scope", "UNION"))
			.execute()
			.path("createOrganization.id").entity(String.class).get();
		List<String> keywords = IntStream.rangeClosed(1, 11).mapToObj(i -> "키워드" + i).toList();

		asUser.document(UPDATE)
			.variable("id", organizationId)
			.variable("input", Map.of(
				"tagline", "소개",
				"fields", List.of("ETC"),
				"keywords", keywords,
				"links", List.of("https://mozip.kr")))
			.execute()
			.errors()
			.satisfy(errors -> {
				assertThat(errors.getFirst().getExtensions()).containsEntry("code", "COMMON_400");
				assertThat(errors.getFirst().getExtensions().get("fieldErrors"))
					.asInstanceOf(InstanceOfAssertFactories.LIST)
					.singleElement()
					.hasFieldOrPropertyWithValue("field", "input.keywords")
					.hasFieldOrPropertyWithValue("message", "키워드는 최대 10개까지 등록 가능해요");
			});
	}

	@Test
	@DisplayName("없는 단체 id면 ORG_404다")
	void organizationNotFound() {
		graphQlTester.document("{ organization(id: \"" + Long.MAX_VALUE + "\") { id } }")
			.execute()
			.errors()
			.satisfy(errors -> {
				assertThat(errors.getFirst().getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
				assertThat(errors.getFirst().getExtensions()).containsEntry("code", "ORG_404");
			});
	}

	@Test
	@DisplayName("드롭다운에서 단체를 고르면 me의 lastOrganization이 바뀌고 lastRole이 OPERATOR가 된다")
	void selectOrganization() {
		User user = saveUser();
		HttpGraphQlTester asUser = asUser(user);
		String organizationId = asUser.document(CREATE)
			.variable("input", Map.of("name", "모집 동아리", "type", "CLUB", "scope", "UNION"))
			.execute()
			.path("createOrganization.id").entity(String.class).get();

		asUser.document(SELECT)
			.variable("organizationId", organizationId)
			.execute()
			.path("selectOrganization.lastRole").entity(String.class).isEqualTo("OPERATOR")
			.path("selectOrganization.lastOrganization.id").entity(String.class).isEqualTo(organizationId);

		asUser.document(ME)
			.execute()
			.path("me.lastRole").entity(String.class).isEqualTo("OPERATOR")
			.path("me.lastOrganization.name").entity(String.class).isEqualTo("모집 동아리");
	}

	@Test
	@DisplayName("소속이 아닌 단체를 고르면 COMMON_403, 없는 단체면 ORG_404다")
	void selectOrganizationForbiddenOrNotFound() {
		HttpGraphQlTester asUser = asUser(saveUser());
		String organizationId = asUser(saveUser()).document(CREATE)
			.variable("input", Map.of("name", "남의 동아리", "type", "CLUB", "scope", "UNION"))
			.execute()
			.path("createOrganization.id").entity(String.class).get();

		asUser.document(SELECT)
			.variable("organizationId", organizationId)
			.execute()
			.errors()
			.satisfy(errors -> {
				assertThat(errors.getFirst().getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
				assertThat(errors.getFirst().getExtensions()).containsEntry("code", "COMMON_403");
			});

		asUser.document(SELECT)
			.variable("organizationId", String.valueOf(Long.MAX_VALUE))
			.execute()
			.errors()
			.satisfy(errors -> assertThat(errors.getFirst().getExtensions()).containsEntry("code", "ORG_404"));
	}

	@Test
	@DisplayName("운영진 목록은 대표자 먼저 나오고, 대표자가 운영진을 내보내면 목록과 me.memberships에서 빠진다")
	void membersThenRemove() {
		User representative = saveUser();
		User operator = saveUser();
		HttpGraphQlTester asRepresentative = asUser(representative);
		HttpGraphQlTester asOperator = asUser(operator);
		String organizationId = asRepresentative.document(CREATE)
			.variable("input", Map.of("name", "모집 동아리", "type", "CLUB", "scope", "UNION"))
			.execute()
			.path("createOrganization.id").entity(String.class).get();
		Organization organization = organizationRepository.findById(Long.valueOf(organizationId)).orElseThrow();
		Member operatorMember = memberRepository.save(Member.create(organization, operator, null, MemberRole.OPERATOR));

		asOperator.document(MEMBERS)
			.variable("organizationId", organizationId)
			.execute()
			.path("members").entityList(Object.class).hasSize(2)
			.path("members[0].role").entity(String.class).isEqualTo("REPRESENTATIVE")
			.path("members[0].user.id").entity(String.class).isEqualTo(representative.getId().toString())
			.path("members[1].id").entity(String.class).isEqualTo(operatorMember.getId().toString())
			.path("members[1].team").valueIsNull();

		asUser(saveUser()).document(MEMBERS)
			.variable("organizationId", organizationId)
			.execute()
			.errors()
			.satisfy(errors -> assertThat(errors.getFirst().getExtensions()).containsEntry("code", "COMMON_403"));

		asOperator.document(REMOVE)
			.variable("memberId", operatorMember.getId().toString())
			.execute()
			.errors()
			.satisfy(errors -> assertThat(errors.getFirst().getExtensions()).containsEntry("code", "COMMON_403"));

		asRepresentative.document(REMOVE)
			.variable("memberId", operatorMember.getId().toString())
			.execute()
			.path("removeMember").entity(Boolean.class).isEqualTo(true);

		asRepresentative.document(MEMBERS)
			.variable("organizationId", organizationId)
			.execute()
			.path("members").entityList(Object.class).hasSize(1);
		asOperator.document(ME)
			.execute()
			.path("me.memberships").entityList(Object.class).hasSize(0);
	}

	@Test
	@DisplayName("대표자 본인은 내보낼 수 없고(MEMBER_409 REPRESENTATIVE), 없는 운영진이면 MEMBER_404다")
	void removeRepresentativeOrUnknown() {
		User representative = saveUser();
		HttpGraphQlTester asRepresentative = asUser(representative);
		String organizationId = asRepresentative.document(CREATE)
			.variable("input", Map.of("name", "모집 동아리", "type", "CLUB", "scope", "UNION"))
			.execute()
			.path("createOrganization.id").entity(String.class).get();
		String representativeMemberId = asRepresentative.document(MEMBERS)
			.variable("organizationId", organizationId)
			.execute()
			.path("members[0].id").entity(String.class).get();

		asRepresentative.document(REMOVE)
			.variable("memberId", representativeMemberId)
			.execute()
			.errors()
			.satisfy(errors -> {
				assertThat(errors.getFirst().getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
				assertThat(errors.getFirst().getExtensions()).containsEntry("code", "MEMBER_409");
				assertThat(errors.getFirst().getExtensions()).containsEntry("reason", "REPRESENTATIVE");
			});

		asRepresentative.document(REMOVE)
			.variable("memberId", String.valueOf(Long.MAX_VALUE))
			.execute()
			.errors()
			.satisfy(errors -> {
				assertThat(errors.getFirst().getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
				assertThat(errors.getFirst().getExtensions()).containsEntry("code", "MEMBER_404");
			});
	}

	@Test
	@DisplayName("me는 로그인이 필요하다")
	void meRequiresLogin() {
		graphQlTester.document("{ me { id } }")
			.execute()
			.errors()
			.satisfy(errors -> assertThat(errors.getFirst().getExtensions()).containsEntry("code", "COMMON_401"));
	}

	@Test
	@DisplayName("학생회인데 세부 구분이 없으면 COMMON_400에 reason COUNCIL_LEVEL_REQUIRED를 담는다")
	void councilLevelRequired() {
		asUser(saveUser()).document(CREATE)
			.variable("input", Map.of("name", "총학생회", "type", "STUDENT_COUNCIL", "scope", "UNION"))
			.execute()
			.errors()
			.satisfy(errors -> {
				assertThat(errors.getFirst().getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
				assertThat(errors.getFirst().getExtensions())
					.containsEntry("code", "COMMON_400")
					.containsEntry("reason", "COUNCIL_LEVEL_REQUIRED");
			});
	}

	@Test
	@DisplayName("이름이 비어 있으면 COMMON_400과 fieldErrors로 응답한다")
	void blankName() {
		asUser(saveUser()).document(CREATE)
			.variable("input", Map.of("name", " ", "type", "CLUB", "scope", "UNION"))
			.execute()
			.errors()
			.satisfy(errors -> {
				assertThat(errors.getFirst().getExtensions()).containsEntry("code", "COMMON_400");
				assertThat(errors.getFirst().getExtensions().get("fieldErrors"))
					.asInstanceOf(InstanceOfAssertFactories.LIST)
					.singleElement()
					.hasFieldOrPropertyWithValue("field", "input.name");
			});
	}

	@Test
	@DisplayName("팀을 저장하고 그 팀으로 초대하면, 초대받은 회원이 코드로 수락해 그 팀 운영진이 된다. 공용 링크는 url을 붙여 준다")
	void saveTeamsThenInviteThenAccept() {
		User representative = saveUser();
		User invitee = saveUser();
		HttpGraphQlTester asRepresentative = asUser(representative);
		String organizationId = asRepresentative.document(CREATE)
			.variable("input", Map.of("name", "초대 동아리", "type", "CLUB", "scope", "UNION"))
			.execute()
			.path("createOrganization.id").entity(String.class).get();

		String teamId = asRepresentative.document("""
				mutation($organizationId: ID!, $teams: [TeamInput!]!) {
					saveTeams(organizationId: $organizationId, teams: $teams) { id name sortOrder }
				}
				""")
			.variable("organizationId", organizationId)
			.variable("teams", List.of(Map.of("name", "기획팀"), Map.of("name", "디자인팀")))
			.execute()
			.path("saveTeams[*].name").entityList(String.class).containsExactly("기획팀", "디자인팀")
			.path("saveTeams[1].sortOrder").entity(Integer.class).isEqualTo(1)
			.path("saveTeams[1].id").entity(String.class).get();

		String code = asRepresentative.document("""
				mutation($organizationId: ID!, $contact: String!, $teamId: ID) {
					inviteMember(organizationId: $organizationId, contact: $contact, teamId: $teamId) {
						status inviteeContact team { name } code
					}
				}
				""")
			.variable("organizationId", organizationId)
			.variable("contact", invitee.getPhone())
			.variable("teamId", teamId)
			.execute()
			.path("inviteMember.status").entity(String.class).isEqualTo("PENDING")
			.path("inviteMember.team.name").entity(String.class).isEqualTo("디자인팀")
			.path("inviteMember.code").entity(String.class).get();

		asUser(invitee).document("""
				mutation($code: String!) {
					acceptInvitation(code: $code) { role team { name } organization { id } user { id } }
				}
				""")
			.variable("code", code)
			.execute()
			.path("acceptInvitation.role").entity(String.class).isEqualTo("OPERATOR")
			.path("acceptInvitation.team.name").entity(String.class).isEqualTo("디자인팀")
			.path("acceptInvitation.organization.id").entity(String.class).isEqualTo(organizationId)
			.path("acceptInvitation.user.id").entity(String.class).isEqualTo(invitee.getId().toString());

		String token = asRepresentative.document("""
				mutation($organizationId: ID!) { createInviteLink(organizationId: $organizationId) { token url } }
				""")
			.variable("organizationId", organizationId)
			.execute()
			.path("createInviteLink.token").entity(String.class).get();
		asRepresentative.document("""
				mutation($organizationId: ID!) { createInviteLink(organizationId: $organizationId) { url } }
				""")
			.variable("organizationId", organizationId)
			.execute()
			.path("createInviteLink.url").entity(String.class).isEqualTo("https://mozip.kr/invite/" + token);
	}

	private String createOrganization(HttpGraphQlTester asUser) {
		return asUser.document(CREATE)
			.variable("input", Map.of("name", "동아리", "type", "CLUB", "scope", "UNION",
				"proofLink", "https://example.com/proof"))
			.execute()
			.path("createOrganization.id").entity(String.class).get();
	}

	// 심사 승인 API가 아직 없어 상태만 직접 바꿈
	private void approve(String organizationId) {
		jdbcTemplate.update("update organizations set review_status = 'APPROVED' where id = ?",
			Long.valueOf(organizationId));
	}

	@Test
	@DisplayName("증빙 링크가 http, https 주소가 아니면 COMMON_400이다")
	void proofLinkMustBeHttp() {
		asUser(saveUser()).document(CREATE)
			.variable("input", Map.of("name", "동아리", "type", "CLUB", "scope", "UNION",
				"proofLink", "javascript:alert(1)"))
			.execute()
			.errors()
			.satisfy(errors -> assertThat(errors.getFirst().getExtensions()).containsEntry("code", "COMMON_400"));
	}

	private User saveUser() {
		String phone = "0109" + String.format("%07d", ThreadLocalRandom.current().nextInt(10_000_000));
		return userRepository.save(User.create(phone, "encoded", "테스트", false, LocalDateTime.now()));
	}

	// MockMvc는 Cookie 헤더 문자열을 파싱하지 않으므로 WebTestClient의 쿠키로 전달
	private HttpGraphQlTester asUser(User user) {
		String accessToken = jwtProvider.createAccessToken(user.getId(), Authority.USER);
		return graphQlTester.mutate()
			.webTestClient(client -> client.defaultCookie(AuthCookieManager.ACCESS_TOKEN_COOKIE, accessToken))
			.build();
	}

}
