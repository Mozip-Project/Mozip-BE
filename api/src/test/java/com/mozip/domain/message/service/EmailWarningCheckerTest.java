package com.mozip.domain.message.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mozip.domain.message.dto.EmailCheck;
import com.mozip.domain.message.dto.EmailWarning;

// 이메일 경고 판정 (DB와 스프링 없이)
class EmailWarningCheckerTest {

	private final EmailWarningChecker checker = new EmailWarningChecker(List.of("cau.ac.kr", "cnu.ac.kr", "SKKU.edu"));

	@Test
	@DisplayName("주소가 없거나 형식이 틀리면 MISSING이고 발송 대상에서 빠진다")
	void missing() {
		for (String email : Arrays.asList(null, "", "   ", "applicant", "@gmail.com", "applicant@", "a b@gmail.com")) {
			EmailCheck check = checker.check(email);

			assertThat(check.warning()).as("email=%s", email).isEqualTo(EmailWarning.MISSING);
			assertThat(check.suggestedDomain()).isNull();
			assertThat(check.sendable()).isFalse();
		}
	}

	@Test
	@DisplayName("자주 쓰는 도메인과 한 글자 다르면 SUSPICIOUS_DOMAIN이고 고칠 도메인을 알려 준다")
	void suspiciousCommonDomain() {
		assertSuspicious("applicant@gmai.com", "gmail.com");
		assertSuspicious("applicant@gmaill.com", "gmail.com");
		assertSuspicious("applicant@gnail.com", "gmail.com");
		assertSuspicious("applicant@naver.con", "naver.com");
		assertSuspicious("applicant@NAVER.CON", "naver.com");
		assertSuspicious("applicant@hanmail.ne", "hanmail.net");
	}

	@Test
	@DisplayName("학교 도메인과 한 글자 다른 주소도 SUSPICIOUS_DOMAIN이다")
	void suspiciousSchoolDomain() {
		assertSuspicious("applicant@skku.ed", "skku.edu");
		assertSuspicious("applicant@cau.ac.kt", "cau.ac.kr");
	}

	@Test
	@DisplayName("아는 도메인과 정확히 같으면 다른 도메인과 한 글자 차이여도 경고하지 않는다")
	void exactMatchIsNotSuspicious() {
		assertOk("applicant@gmail.com");
		assertOk("applicant@Gmail.com");
		assertOk("  applicant@naver.com  ");
		assertOk("applicant@cau.ac.kr");
		assertOk("applicant@cnu.ac.kr");
		assertOk("applicant@skku.edu");
	}

	@Test
	@DisplayName("모르는 도메인이거나 두 글자 넘게 다르면 경고하지 않는다")
	void unknownDomainIsNotSuspicious() {
		assertOk("applicant@company.co.kr");
		assertOk("applicant@gmal.co");
		// 글자 순서가 바뀐 오타는 편집 두 번으로 세어 잡지 않음
		assertOk("applicant@gmial.com");
	}

	@Test
	@DisplayName("학교 도메인 목록에 null이나 빈 값이 섞여 있어도 된다")
	void ignoresBlankSchoolDomains() {
		EmailWarningChecker withBlanks = new EmailWarningChecker(Arrays.asList(null, " ", "cau.ac.kr"));

		assertThat(withBlanks.check("applicant@cau.ac.kr").warning()).isNull();
		assertThat(withBlanks.check("applicant@cau.ac.k").suggestedDomain()).isEqualTo("cau.ac.kr");
	}

	private void assertSuspicious(String email, String suggestedDomain) {
		EmailCheck check = checker.check(email);

		assertThat(check.warning()).as("email=%s", email).isEqualTo(EmailWarning.SUSPICIOUS_DOMAIN);
		assertThat(check.suggestedDomain()).isEqualTo(suggestedDomain);
		assertThat(check.sendable()).isFalse();
	}

	private void assertOk(String email) {
		EmailCheck check = checker.check(email);

		assertThat(check.warning()).as("email=%s", email).isNull();
		assertThat(check.suggestedDomain()).isNull();
		assertThat(check.sendable()).isTrue();
	}

}
