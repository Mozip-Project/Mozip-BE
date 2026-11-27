package com.mozip.domain.school.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.TestcontainersConfiguration;
import com.mozip.domain.mail.exception.MailErrorCode;
import com.mozip.domain.mail.service.MailTaskPublisher;
import com.mozip.domain.school.entity.School;
import com.mozip.domain.school.repository.SchoolRepository;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.exception.BusinessException;

// worker에 메일 작업을 못 넘기는 경우(Redis 발행 실패)를 흉내내, 방금 저장한 인증번호가 정리되는지 확인
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class SchoolVerificationMailFailureTest {

	@Autowired
	private SchoolVerificationService schoolVerificationService;

	@Autowired
	private SchoolRepository schoolRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@MockitoBean
	private MailTaskPublisher mailTaskPublisher;

	private User applicant;
	private String email;

	@BeforeEach
	void setUp() {
		String phone = "010" + ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999);
		applicant = userRepository.save(User.create(phone, "encoded", "지원자", false, LocalDateTime.now()));
		School school = schoolRepository.save(School.create("메일 실패 테스트 대학교 " + ThreadLocalRandom.current().nextInt(1_000_000),
			"mailfail" + ThreadLocalRandom.current().nextInt(1_000_000) + ".ac.kr"));
		email = "student@" + school.getEmailDomain();
		doThrow(new BusinessException(MailErrorCode.SEND_FAILED))
			.when(mailTaskPublisher)
			.publishSchoolVerificationMail(any(), any(), any());
	}

	@Test
	@DisplayName("worker에 메일 작업을 못 넘기면 MAIL_503을 던지고 방금 저장한 인증번호를 지운다")
	void cleansUpCodeWhenPublishFails() {
		assertThatThrownBy(() -> schoolVerificationService.requestSchoolEmailVerification(applicant.getId(), email))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(MailErrorCode.SEND_FAILED);

		assertThat(redisTemplate.hasKey("school:verify:code:" + email)).isFalse();
	}

}
