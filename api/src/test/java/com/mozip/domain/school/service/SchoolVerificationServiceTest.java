package com.mozip.domain.school.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.TestcontainersConfiguration;
import com.mozip.domain.school.entity.School;
import com.mozip.domain.school.entity.SchoolEmailVerificationStatus;
import com.mozip.domain.school.entity.SchoolVerification;
import com.mozip.domain.school.exception.SchoolConflictReason;
import com.mozip.domain.school.exception.SchoolErrorCode;
import com.mozip.domain.school.exception.SchoolInputReason;
import com.mozip.domain.school.repository.SchoolEmailVerificationCodeRepository;
import com.mozip.domain.school.repository.SchoolRepository;
import com.mozip.domain.school.repository.SchoolVerificationRepository;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.exception.BusinessException;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class SchoolVerificationServiceTest {

	@Autowired
	private SchoolVerificationService schoolVerificationService;

	@Autowired
	private SchoolRepository schoolRepository;

	@Autowired
	private SchoolVerificationRepository schoolVerificationRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private StringRedisTemplate redisTemplate;

	private User applicant;
	private School school;
	private String email;

	@BeforeEach
	void setUp() {
		String phone = "010" + ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999);
		applicant = userRepository.save(User.create(phone, "encoded", "지원자", false, LocalDateTime.now()));
		school = schoolRepository.save(School.create("테스트대학교 " + ThreadLocalRandom.current().nextInt(1_000_000),
			"school" + ThreadLocalRandom.current().nextInt(1_000_000) + ".ac.kr"));
		email = "student@" + school.getEmailDomain();
	}

	@Test
	@DisplayName("학교 이름으로 검색하면 일치하는 학교만 반환한다")
	void searchFindsMatchingSchools() {
		List<School> result = schoolVerificationService.search(school.getName().substring(0, 5));

		assertThat(result).extracting(School::getId).contains(school.getId());
	}

	@Test
	@DisplayName("keyword가 비어 있으면 빈 목록을 반환한다")
	void searchReturnsEmptyForBlankKeyword() {
		assertThat(schoolVerificationService.search(" ")).isEmpty();
		assertThat(schoolVerificationService.search(null)).isEmpty();
	}

	@Test
	@DisplayName("메일 도메인과 일치하는 학교가 없으면 SCHOOL_404를 던진다")
	void rejectsUnknownEmailDomain() {
		assertThatThrownBy(() -> schoolVerificationService.requestSchoolEmailVerification(applicant.getId(),
			"student@unknown-domain.ac.kr"))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(SchoolErrorCode.NOT_FOUND);
	}

	@Test
	@DisplayName("인증번호를 요청하고 그 번호로 verifySchoolEmail을 호출하면 true를 반환하고, 코드를 지우고 confirmed로 표시한다")
	void requestAndVerifySchoolEmailSucceedsAndMarksConfirmed() {
		schoolVerificationService.requestSchoolEmailVerification(applicant.getId(), email);
		String code = redisTemplate.opsForValue().get("school:verify:code:" + email);
		assertThat(code).matches("\\d{6}");

		boolean result = schoolVerificationService.verifySchoolEmail(email, code);

		assertThat(result).isTrue();
		assertThat(redisTemplate.hasKey("school:verify:code:" + email)).isFalse();
		School verifiedSchool = schoolVerificationService.verifyForSignup(email);
		assertThat(verifiedSchool.getId()).isEqualTo(school.getId());
	}

	@Test
	@DisplayName("이미 확인을 마친 메일로 verifySchoolEmail을 다시 호출하면 SCHOOL_409(EMAIL_ALREADY_CONFIRMED)를 던진다")
	void verifySchoolEmailRejectsAlreadyConfirmedEmail() {
		schoolVerificationService.requestSchoolEmailVerification(applicant.getId(), email);
		String code = redisTemplate.opsForValue().get("school:verify:code:" + email);
		schoolVerificationService.verifySchoolEmail(email, code);

		assertThatThrownBy(() -> schoolVerificationService.verifySchoolEmail(email, code))
			.isInstanceOf(BusinessException.class)
			.satisfies(e -> {
				BusinessException ex = (BusinessException)e;
				assertThat(ex.getErrorCode()).isEqualTo(SchoolErrorCode.CONFLICT);
				assertThat(ex.getReason()).isEqualTo(SchoolConflictReason.EMAIL_ALREADY_CONFIRMED.name());
			});
	}

	@Test
	@DisplayName("verifySchoolEmail은 메일 도메인과 일치하는 학교가 없으면 에러 없이 false를 반환한다")
	void verifySchoolEmailReturnsFalseForUnknownDomain() {
		assertThat(schoolVerificationService.verifySchoolEmail("student@unknown-domain.ac.kr", "123456")).isFalse();
	}

	@Test
	@DisplayName("verifySchoolEmail로 확인한 적 없으면 verifyForSignup은 SCHOOL_400(EMAIL_NOT_VERIFIED)을 던진다")
	void verifyForSignupRejectsUnconfirmedEmail() {
		assertThatThrownBy(() -> schoolVerificationService.verifyForSignup(email))
			.isInstanceOf(BusinessException.class)
			.satisfies(e -> {
				BusinessException ex = (BusinessException)e;
				assertThat(ex.getErrorCode()).isEqualTo(SchoolErrorCode.INVALID_INPUT);
				assertThat(ex.getReason()).isEqualTo(SchoolInputReason.EMAIL_NOT_VERIFIED.name());
			});
	}

	@Test
	@DisplayName("인증번호를 요청하면 emailVerificationStatus가 PENDING이 된다 (true는 접수됐다는 뜻일 뿐, 발송 자체는 worker가 비동기로 확정)")
	void requestMarksStatusPending() {
		schoolVerificationService.requestSchoolEmailVerification(applicant.getId(), email);

		assertThat(schoolVerificationService.emailVerificationStatus(email))
			.isEqualTo(SchoolEmailVerificationStatus.PENDING);
	}

	@Test
	@DisplayName("요청한 적 없는 이메일의 emailVerificationStatus는 null이다")
	void statusIsNullWithoutRequest() {
		assertThat(schoolVerificationService.emailVerificationStatus(email)).isNull();
	}

	@Test
	@DisplayName("짧은 시간에 반복 요청하면 SCHOOL_429를 던진다")
	void rejectsRepeatedRequestsWithinCooldown() {
		schoolVerificationService.requestSchoolEmailVerification(applicant.getId(), email);

		assertThatThrownBy(() -> schoolVerificationService.requestSchoolEmailVerification(applicant.getId(), email))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(SchoolErrorCode.TOO_MANY_REQUESTS);
	}

	@Test
	@DisplayName("인증번호가 틀리면 에러 없이 false를 반환한다")
	void verifySchoolEmailReturnsFalseForWrongCode() {
		schoolVerificationService.requestSchoolEmailVerification(applicant.getId(), email);

		assertThat(schoolVerificationService.verifySchoolEmail(email, "000000")).isFalse();
	}

	@Test
	@DisplayName("요청한 적 없는 인증번호를 확인하면 에러 없이 false를 반환한다")
	void verifySchoolEmailReturnsFalseWithoutRequesting() {
		assertThat(schoolVerificationService.verifySchoolEmail(email, "123456")).isFalse();
	}

	@Test
	@DisplayName("틀린 횟수가 5번을 넘으면 맞는 코드를 다시 넣어도 false를 반환한다 (서버가 코드를 먼저 지움)")
	void invalidatesCodeAfterTooManyWrongAttempts() {
		schoolVerificationService.requestSchoolEmailVerification(applicant.getId(), email);
		String code = redisTemplate.opsForValue().get("school:verify:code:" + email);

		for (int i = 0; i < SchoolEmailVerificationCodeRepository.MAX_ATTEMPTS; i++) {
			assertThat(schoolVerificationService.verifySchoolEmail(email, "000000")).isFalse();
		}

		assertThat(schoolVerificationService.verifySchoolEmail(email, code)).isFalse();
	}

	@Test
	@DisplayName("userId가 없어도(로그인 없이) 인증번호를 요청할 수 있다")
	void requestSucceedsWithoutUserId() {
		boolean result = schoolVerificationService.requestSchoolEmailVerification(null, email);

		assertThat(result).isTrue();
		assertThat(redisTemplate.opsForValue().get("school:verify:code:" + email)).matches("\\d{6}");
	}

	@Test
	@DisplayName("이미 학교 인증된 회원이 다시 메일 인증을 요청하면 SCHOOL_409(ALREADY_VERIFIED)를 던진다")
	void rejectsReVerificationByEmail() {
		applicant.verifySchool(school);

		assertThatThrownBy(() -> schoolVerificationService.requestSchoolEmailVerification(applicant.getId(), email))
			.isInstanceOf(BusinessException.class)
			.satisfies(e -> {
				BusinessException ex = (BusinessException)e;
				assertThat(ex.getErrorCode()).isEqualTo(SchoolErrorCode.CONFLICT);
				assertThat(ex.getReason()).isEqualTo(SchoolConflictReason.ALREADY_VERIFIED.name());
			});
	}

	@Test
	@DisplayName("이미 그 학교 메일로 인증을 끝낸 적이 있으면 로그인 없이 요청해도 SCHOOL_409(EMAIL_ALREADY_VERIFIED)를 던진다")
	void rejectsRequestForAlreadyVerifiedEmail() {
		schoolVerificationRepository.save(SchoolVerification.createApprovedEmail(applicant, school, email));

		assertThatThrownBy(() -> schoolVerificationService.requestSchoolEmailVerification(null, email))
			.isInstanceOf(BusinessException.class)
			.satisfies(e -> {
				BusinessException ex = (BusinessException)e;
				assertThat(ex.getErrorCode()).isEqualTo(SchoolErrorCode.CONFLICT);
				assertThat(ex.getReason()).isEqualTo(SchoolConflictReason.EMAIL_ALREADY_VERIFIED.name());
			});
	}

}
