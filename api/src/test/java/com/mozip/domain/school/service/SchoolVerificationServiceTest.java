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
import com.mozip.domain.school.entity.SchoolVerification;
import com.mozip.domain.school.exception.SchoolConflictReason;
import com.mozip.domain.school.exception.SchoolErrorCode;
import com.mozip.domain.school.exception.SchoolInputReason;
import com.mozip.domain.school.repository.SchoolEmailVerificationCodeRepository;
import com.mozip.domain.school.repository.SchoolRepository;
import com.mozip.domain.school.repository.SchoolVerificationRepository;
import com.mozip.domain.user.dto.Me;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.common.ReviewStatus;
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
	@DisplayName("인증번호를 요청하면 10분 TTL로 Redis에 저장되고, 그 번호로 인증하면 회원 학교가 채워진다")
	void requestAndVerifySchoolEmailSucceeds() {
		schoolVerificationService.requestSchoolEmailVerification(applicant.getId(), email);
		String code = redisTemplate.opsForValue().get("school:verify:code:" + email);
		assertThat(code).matches("\\d{6}");

		Me me = schoolVerificationService.verifySchoolEmail(applicant.getId(), email, code);

		assertThat(me.school()).isNotNull();
		assertThat(me.school().getId()).isEqualTo(school.getId());
		assertThat(redisTemplate.hasKey("school:verify:code:" + email)).isFalse();
		List<SchoolVerification> saved = schoolVerificationRepository.findAll().stream()
			.filter(v -> v.getUser().getId().equals(applicant.getId()))
			.toList();
		assertThat(saved).singleElement()
			.satisfies(v -> {
				assertThat(v.getStatus()).isEqualTo(ReviewStatus.APPROVED);
				assertThat(v.getEmail()).isEqualTo(email);
			});
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
	@DisplayName("인증번호가 틀리면 SCHOOL_400(CODE_MISMATCH)을 던진다")
	void rejectsWrongCode() {
		schoolVerificationService.requestSchoolEmailVerification(applicant.getId(), email);

		assertThatThrownBy(() -> schoolVerificationService.verifySchoolEmail(applicant.getId(), email, "000000"))
			.isInstanceOf(BusinessException.class)
			.satisfies(e -> {
				BusinessException ex = (BusinessException)e;
				assertThat(ex.getErrorCode()).isEqualTo(SchoolErrorCode.INVALID_INPUT);
				assertThat(ex.getReason()).isEqualTo(SchoolInputReason.CODE_MISMATCH.name());
			});
	}

	@Test
	@DisplayName("요청한 적 없는 인증번호를 확인하면 SCHOOL_400(CODE_EXPIRED)을 던진다")
	void rejectsVerifyingWithoutRequesting() {
		assertThatThrownBy(() -> schoolVerificationService.verifySchoolEmail(applicant.getId(), email, "123456"))
			.isInstanceOf(BusinessException.class)
			.satisfies(e -> {
				BusinessException ex = (BusinessException)e;
				assertThat(ex.getErrorCode()).isEqualTo(SchoolErrorCode.INVALID_INPUT);
				assertThat(ex.getReason()).isEqualTo(SchoolInputReason.CODE_EXPIRED.name());
			});
	}

	@Test
	@DisplayName("틀린 횟수가 5번을 넘으면 맞는 코드를 다시 넣어도 SCHOOL_400(CODE_EXPIRED)를 던진다")
	void invalidatesCodeAfterTooManyWrongAttempts() {
		schoolVerificationService.requestSchoolEmailVerification(applicant.getId(), email);
		String code = redisTemplate.opsForValue().get("school:verify:code:" + email);

		for (int i = 0; i < SchoolEmailVerificationCodeRepository.MAX_ATTEMPTS; i++) {
			assertThatThrownBy(
				() -> schoolVerificationService.verifySchoolEmail(applicant.getId(), email, "000000"))
				.isInstanceOf(BusinessException.class);
		}

		assertThatThrownBy(() -> schoolVerificationService.verifySchoolEmail(applicant.getId(), email, code))
			.isInstanceOf(BusinessException.class)
			.satisfies(e -> assertThat(((BusinessException)e).getReason())
				.isEqualTo(SchoolInputReason.CODE_EXPIRED.name()));
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
	@DisplayName("재학증명서를 제출하면 PENDING으로 저장된다")
	void submitsCertificateAsPending() {
		SchoolVerification verification = schoolVerificationService.submitEnrollmentCertificate(applicant.getId(),
			school.getId(), "enrollment-cert/2026/11/" + ThreadLocalRandom.current().nextInt() + ".pdf");

		assertThat(verification.getStatus()).isEqualTo(ReviewStatus.PENDING);
		assertThat(verification.getSchool().getId()).isEqualTo(school.getId());
	}

	@Test
	@DisplayName("다른 용도로 발급된 fileKey로 재학증명서를 제출하면 COMMON_400을 던진다")
	void rejectsCertificateWithWrongFileKeyPrefix() {
		assertThatThrownBy(() -> schoolVerificationService.submitEnrollmentCertificate(applicant.getId(),
			school.getId(), "posting-image/2026/11/abc.png"))
			.isInstanceOf(BusinessException.class);
	}

	@Test
	@DisplayName("이미 PENDING인 재학증명서가 있으면 다시 제출해도 SCHOOL_409(PENDING_EXISTS)를 던진다")
	void rejectsSecondCertificateWhilePending() {
		schoolVerificationService.submitEnrollmentCertificate(applicant.getId(), school.getId(),
			"enrollment-cert/2026/11/first.pdf");

		assertThatThrownBy(() -> schoolVerificationService.submitEnrollmentCertificate(applicant.getId(),
			school.getId(), "enrollment-cert/2026/11/second.pdf"))
			.isInstanceOf(BusinessException.class)
			.satisfies(e -> {
				BusinessException ex = (BusinessException)e;
				assertThat(ex.getErrorCode()).isEqualTo(SchoolErrorCode.CONFLICT);
				assertThat(ex.getReason()).isEqualTo(SchoolConflictReason.PENDING_EXISTS.name());
			});
	}

	@Test
	@DisplayName("이미 학교 인증된 회원이 재학증명서를 제출하면 SCHOOL_409(ALREADY_VERIFIED)를 던진다")
	void rejectsCertificateFromAlreadyVerifiedUser() {
		applicant.verifySchool(school);

		assertThatThrownBy(() -> schoolVerificationService.submitEnrollmentCertificate(applicant.getId(),
			school.getId(), "enrollment-cert/2026/11/abc.pdf"))
			.isInstanceOf(BusinessException.class)
			.satisfies(e -> {
				BusinessException ex = (BusinessException)e;
				assertThat(ex.getErrorCode()).isEqualTo(SchoolErrorCode.CONFLICT);
				assertThat(ex.getReason()).isEqualTo(SchoolConflictReason.ALREADY_VERIFIED.name());
			});
	}

}
