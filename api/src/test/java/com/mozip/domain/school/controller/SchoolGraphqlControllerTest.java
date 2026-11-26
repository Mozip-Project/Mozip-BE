package com.mozip.domain.school.controller;

import static org.assertj.core.api.Assertions.assertThat;

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
	@DisplayName("메일 인증번호를 요청하고 확인하면(verifySchoolEmail) true를 반환하고, 코드를 지우고 confirmed로 표시한다")
	void requestAndVerifySchoolEmailSucceedsAndMarksConfirmed() {
		School school = schoolRepository.save(School.create("이메일인증테스트대학교 " + ThreadLocalRandom.current().nextInt(1_000_000),
			"emailverify" + ThreadLocalRandom.current().nextInt(1_000_000) + ".ac.kr"));
		String email = "student@" + school.getEmailDomain();

		graphQlTester.document("""
			mutation($email: String!) {
				requestSchoolEmailVerification(email: $email)
			}
			""")
			.variable("email", email)
			.execute()
			.path("requestSchoolEmailVerification").entity(Boolean.class).isEqualTo(true);

		String code = redisTemplate.opsForValue().get("school:verify:code:" + email);
		assertThat(code).isNotNull();

		graphQlTester.document("""
			mutation($email: String!, $code: String!) {
				verifySchoolEmail(email: $email, code: $code)
			}
			""")
			.variable("email", email)
			.variable("code", code)
			.execute()
			.path("verifySchoolEmail").entity(Boolean.class).isEqualTo(true);

		assertThat(redisTemplate.hasKey("school:verify:code:" + email)).isFalse();
		assertThat(redisTemplate.hasKey("school:verify:confirmed:" + email)).isTrue();
	}

	@Test
	@DisplayName("이미 확인을 마친 메일로 verifySchoolEmail을 다시 호출하면 SCHOOL_409(EMAIL_ALREADY_CONFIRMED)로 응답한다")
	void verifySchoolEmailRejectsAlreadyConfirmedEmail() {
		School school = schoolRepository.save(School.create("중복확인테스트대학교 " + ThreadLocalRandom.current().nextInt(1_000_000),
			"doubleconfirm" + ThreadLocalRandom.current().nextInt(1_000_000) + ".ac.kr"));
		String email = "student@" + school.getEmailDomain();
		graphQlTester.document("""
			mutation($email: String!) {
				requestSchoolEmailVerification(email: $email)
			}
			""")
			.variable("email", email)
			.execute();
		String code = redisTemplate.opsForValue().get("school:verify:code:" + email);
		graphQlTester.document("""
			mutation($email: String!, $code: String!) {
				verifySchoolEmail(email: $email, code: $code)
			}
			""")
			.variable("email", email)
			.variable("code", code)
			.execute();

		graphQlTester.document("""
			mutation($email: String!, $code: String!) {
				verifySchoolEmail(email: $email, code: $code)
			}
			""")
			.variable("email", email)
			.variable("code", code)
			.execute()
			.errors()
			.satisfy(errors -> {
				assertThat(errors.getFirst().getExtensions()).containsEntry("code", "SCHOOL_409");
				assertThat(errors.getFirst().getExtensions()).containsEntry("reason", "EMAIL_ALREADY_CONFIRMED");
			});
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
	@DisplayName("인증번호가 틀리면 에러 없이 false로 응답한다")
	void wrongCodeRespondsWithFalse() {
		School school = schoolRepository.save(School.create("오류테스트대학교 " + ThreadLocalRandom.current().nextInt(1_000_000),
			"errtest" + ThreadLocalRandom.current().nextInt(1_000_000) + ".ac.kr"));
		String email = "student@" + school.getEmailDomain();
		graphQlTester.document("""
			mutation($email: String!) {
				requestSchoolEmailVerification(email: $email)
			}
			""")
			.variable("email", email)
			.execute();

		graphQlTester.document("""
			mutation($email: String!, $code: String!) {
				verifySchoolEmail(email: $email, code: $code)
			}
			""")
			.variable("email", email)
			.variable("code", "000000")
			.execute()
			.path("verifySchoolEmail").entity(Boolean.class).isEqualTo(false);
	}

}
