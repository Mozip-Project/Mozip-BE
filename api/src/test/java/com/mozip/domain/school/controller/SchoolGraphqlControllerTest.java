package com.mozip.domain.school.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.graphql.test.tester.HttpGraphQlTester;

import com.mozip.TestcontainersConfiguration;
import com.mozip.domain.school.entity.School;
import com.mozip.domain.school.repository.SchoolRepository;
import com.mozip.domain.user.entity.Authority;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.security.cookie.AuthCookieManager;
import com.mozip.global.security.jwt.JwtProvider;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureHttpGraphQlTester
class SchoolGraphqlControllerTest {

	@Autowired
	private HttpGraphQlTester graphQlTester;

	@Autowired
	private SchoolRepository schoolRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JwtProvider jwtProvider;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Test
	@DisplayName("schools는 로그인 없이도 키워드와 일치하는 학교를 반환한다")
	void schoolsIsPublicAndSearchesByKeyword() {
		School school = schoolRepository.save(School.create("그래프큐엘테스트대학교 " + ThreadLocalRandom.current().nextInt(1_000_000),
			"gqltest" + ThreadLocalRandom.current().nextInt(1_000_000) + ".ac.kr"));

		graphQlTester.document("""
			query($keyword: String) {
				schools(keyword: $keyword) { id name }
			}
			""")
			.variable("keyword", "그래프큐엘테스트대학교")
			.execute()
			.path("schools[0].id").entity(String.class).isEqualTo(String.valueOf(school.getId()));
	}

	@Test
	@DisplayName("메일 인증번호를 요청하고 확인하면 Me.school이 채워진다")
	void requestAndVerifySchoolEmailEndToEnd() {
		School school = schoolRepository.save(School.create("이메일인증테스트대학교 " + ThreadLocalRandom.current().nextInt(1_000_000),
			"emailverify" + ThreadLocalRandom.current().nextInt(1_000_000) + ".ac.kr"));
		String phone = "010" + ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999);
		User applicant = userRepository.save(User.create(phone, "encoded", "지원자", false, LocalDateTime.now()));
		String email = "student@" + school.getEmailDomain();
		HttpGraphQlTester asApplicant = asUser(applicant);

		asApplicant.document("""
			mutation($email: String!) {
				requestSchoolEmailVerification(email: $email)
			}
			""")
			.variable("email", email)
			.execute()
			.path("requestSchoolEmailVerification").entity(Boolean.class).isEqualTo(true);

		String code = redisTemplate.opsForValue().get("school:verify:code:" + email);
		assertThat(code).isNotNull();

		asApplicant.document("""
			mutation($email: String!, $code: String!) {
				verifySchoolEmail(email: $email, code: $code) {
					school { id }
				}
			}
			""")
			.variable("email", email)
			.variable("code", code)
			.execute()
			.path("verifySchoolEmail.school.id").entity(String.class).isEqualTo(String.valueOf(school.getId()));
	}

	@Test
	@DisplayName("requestSchoolEmailVerification은 로그인 없이도(쿠키 없이) 호출할 수 있다")
	void requestSchoolEmailVerificationIsPublic() {
		School school = schoolRepository.save(School.create("비로그인테스트대학교 " + ThreadLocalRandom.current().nextInt(1_000_000),
			"noauth" + ThreadLocalRandom.current().nextInt(1_000_000) + ".ac.kr"));
		String email = "student@" + school.getEmailDomain();

		graphQlTester.document("""
			mutation($email: String!) {
				requestSchoolEmailVerification(email: $email)
			}
			""")
			.variable("email", email)
			.execute()
			.path("requestSchoolEmailVerification").entity(Boolean.class).isEqualTo(true);

		assertThat(redisTemplate.opsForValue().get("school:verify:code:" + email)).isNotNull();
	}

	@Test
	@DisplayName("schoolEmailVerificationStatus는 로그인 없이도 요청 접수 상태(PENDING)를 반환한다")
	void schoolEmailVerificationStatusIsPublicAndReflectsPending() {
		School school = schoolRepository.save(School.create("상태조회테스트대학교 " + ThreadLocalRandom.current().nextInt(1_000_000),
			"statuscheck" + ThreadLocalRandom.current().nextInt(1_000_000) + ".ac.kr"));
		String email = "student@" + school.getEmailDomain();

		graphQlTester.document("""
			mutation($email: String!) {
				requestSchoolEmailVerification(email: $email)
			}
			""")
			.variable("email", email)
			.execute();

		graphQlTester.document("""
			query($email: String!) {
				schoolEmailVerificationStatus(email: $email)
			}
			""")
			.variable("email", email)
			.execute()
			.path("schoolEmailVerificationStatus").entity(String.class).isEqualTo("PENDING");
	}

	@Test
	@DisplayName("요청한 적 없는 이메일의 schoolEmailVerificationStatus는 null이다")
	void schoolEmailVerificationStatusIsNullWithoutRequest() {
		graphQlTester.document("""
			query($email: String!) {
				schoolEmailVerificationStatus(email: $email)
			}
			""")
			.variable("email", "nobody-" + ThreadLocalRandom.current().nextInt(1_000_000) + "@example.com")
			.execute()
			.path("schoolEmailVerificationStatus").valueIsNull();
	}

	@Test
	@DisplayName("인증번호가 틀리면 SCHOOL_400과 reason(CODE_MISMATCH)으로 응답한다")
	void wrongCodeRespondsWithSchool400() {
		School school = schoolRepository.save(School.create("오류테스트대학교 " + ThreadLocalRandom.current().nextInt(1_000_000),
			"errtest" + ThreadLocalRandom.current().nextInt(1_000_000) + ".ac.kr"));
		String phone = "010" + ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999);
		User applicant = userRepository.save(User.create(phone, "encoded", "지원자", false, LocalDateTime.now()));
		String email = "student@" + school.getEmailDomain();
		HttpGraphQlTester asApplicant = asUser(applicant);
		asApplicant.document("""
			mutation($email: String!) {
				requestSchoolEmailVerification(email: $email)
			}
			""")
			.variable("email", email)
			.execute();

		asApplicant.document("""
			mutation($email: String!, $code: String!) {
				verifySchoolEmail(email: $email, code: $code) { id }
			}
			""")
			.variable("email", email)
			.variable("code", "000000")
			.execute()
			.errors()
			.satisfy(errors -> {
				assertThat(errors.getFirst().getExtensions()).containsEntry("code", "SCHOOL_400");
				assertThat(errors.getFirst().getExtensions()).containsEntry("reason", "CODE_MISMATCH");
			});
	}

	private HttpGraphQlTester asUser(User user) {
		String accessToken = jwtProvider.createAccessToken(user.getId(), Authority.USER);
		return graphQlTester.mutate()
			.webTestClient(client -> client.defaultCookie(AuthCookieManager.ACCESS_TOKEN_COOKIE, accessToken))
			.build();
	}

}
