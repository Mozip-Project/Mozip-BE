package com.mozip.domain.organization.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.HttpGraphQlTester;

import com.mozip.TestcontainersConfiguration;
import com.mozip.domain.school.entity.School;
import com.mozip.domain.school.repository.SchoolRepository;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.domain.user.entity.Authority;
import com.mozip.global.security.cookie.AuthCookieManager;
import com.mozip.global.security.jwt.JwtProvider;

// 롤백하지 않는 테스트라 회원마다 무작위 전화번호를 씀
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

	@Autowired
	private HttpGraphQlTester graphQlTester;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private SchoolRepository schoolRepository;

	@Autowired
	private JwtProvider jwtProvider;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("단체를 만들면 내 단체 목록에 나오고, 심사 전 상세는 소속 운영진만 볼 수 있다")
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

		asUser.document("{ myOrganizations { id name } }")
			.execute()
			.path("myOrganizations[*].id").entityList(String.class).containsExactly(organizationId);

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
	@DisplayName("내 단체 목록은 로그인이 필요하다")
	void myOrganizationsRequiresLogin() {
		graphQlTester.document("{ myOrganizations { id } }")
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

	private HttpGraphQlTester asUser(User user) {
		String accessToken = jwtProvider.createAccessToken(user.getId(), Authority.USER);
		return graphQlTester.mutate()
			.webTestClient(client -> client.defaultCookie(AuthCookieManager.ACCESS_TOKEN_COOKIE, accessToken))
			.build();
	}

}
