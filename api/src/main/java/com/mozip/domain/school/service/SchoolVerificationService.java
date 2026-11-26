package com.mozip.domain.school.service;

import java.security.SecureRandom;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.domain.mail.service.MailTaskPublisher;
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
import com.mozip.domain.user.exception.UserErrorCode;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.common.ReviewStatus;
import com.mozip.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchoolVerificationService {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final SchoolRepository schoolRepository;
	private final SchoolVerificationRepository schoolVerificationRepository;
	private final SchoolEmailVerificationCodeRepository codeRepository;
	private final UserRepository userRepository;
	private final MailTaskPublisher mailTaskPublisher;

	// 단체 만들기, 학교 인증 화면 자동완성
	public List<School> search(String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return List.of();
		}
		return schoolRepository.findTop20ByNameContainingIgnoreCaseOrderByName(keyword.trim());
	}

	// 요청한 적 없거나 TTL(5분)이 지났으면 null. 클라이언트가 requestSchoolEmailVerification 이후 폴링으로 확인
	public SchoolEmailVerificationStatus emailVerificationStatus(String email) {
		return codeRepository.findDeliveryStatus(email).orElse(null);
	}

	// userId는 로그인한 상태로 호출했을 때만 있음(로그인 없이도 호출 가능, @public). 없으면 이미 인증된 회원인지는 건너뜀
	@Transactional
	public boolean requestSchoolEmailVerification(Long userId, String email) {
		if (userId != null) {
			requireNotVerified(findUser(userId));
		}
		School school = findSchoolByEmailDomain(email);
		if (schoolVerificationRepository.existsByEmailAndStatus(email, ReviewStatus.APPROVED)) {
			throw new BusinessException(SchoolErrorCode.CONFLICT, SchoolConflictReason.EMAIL_ALREADY_VERIFIED)
				.addContext("email", email);
		}
		if (codeRepository.isOnCooldown(email)) {
			throw new BusinessException(SchoolErrorCode.TOO_MANY_REQUESTS).addContext("email", email);
		}

		String code = generateCode();
		codeRepository.markRequested(email);
		codeRepository.saveCode(email, code);
		try {
			mailTaskPublisher.publishSchoolVerificationMail(email, mailSubject(), mailBody(school.getName(), code));
		} catch (RuntimeException e) {
			// worker로 작업을 넘기지 못함. 다시 요청해야 하므로 방금 저장한 인증번호는 지움
			codeRepository.deleteCode(email);
			throw e;
		}
		// 여기까지는 Redis에 작업을 넘긴 것만 보장. 실제 발송 성공/실패는 worker가 markSent/markFailed로 남기고
		// schoolEmailVerificationStatus로 조회함 (true는 "요청이 접수됐다"는 뜻이지 "메일이 갔다"는 뜻이 아님)
		codeRepository.markPending(email);
		return true;
	}

	// 가입 폼의 나머지 입력(전화번호, 비밀번호 등)을 채우기 전에 코드가 맞는지만 확인. 로그인 불필요(@public).
	// 코드를 지우지 않아서, 실제 가입(verifyForSignup)에서 같은 코드를 그대로 다시 씀
	@Transactional
	public boolean verifySchoolEmail(String email, String code) {
		requireValidCode(email, code);
		return true;
	}

	// 회원가입 시 학교 메일 인증을 끝냄. 아직 User가 없어 School만 반환하고, 가입 자체는 호출자(AuthService)가 처리.
	// 성공하면 코드가 지워지므로, 이후 가입이 다른 이유(중복 번호 등)로 실패해도 같은 코드로 다시 시도할 수 없음
	@Transactional
	public School verifyForSignup(String email, String code) {
		return consumeValidCode(email, code);
	}

	// 가입으로 생성된 User에 학교 인증 완료를 반영. verifyForSignup과 분리된 건 User 저장(가입) 전에는
	// school_verifications.user_id를 채울 수 없기 때문
	@Transactional
	public void completeSignupVerification(User user, School school, String email) {
		schoolVerificationRepository.save(SchoolVerification.createApprovedEmail(user, school, email));
		user.verifySchool(school);
	}

	private School consumeValidCode(String email, String code) {
		School school = requireValidCode(email, code);
		codeRepository.deleteCode(email);
		return school;
	}

	private School requireValidCode(String email, String code) {
		School school = findSchoolByEmailDomain(email);

		String savedCode = codeRepository.findCode(email)
			.orElseThrow(() -> new BusinessException(SchoolErrorCode.INVALID_INPUT, SchoolInputReason.CODE_EXPIRED)
				.addContext("email", email));
		if (!savedCode.equals(code)) {
			codeRepository.recordWrongAttempt(email);
			throw new BusinessException(SchoolErrorCode.INVALID_INPUT, SchoolInputReason.CODE_MISMATCH)
				.addContext("email", email);
		}
		return school;
	}

	private void requireNotVerified(User user) {
		if (user.isSchoolVerified()) {
			throw new BusinessException(SchoolErrorCode.CONFLICT, SchoolConflictReason.ALREADY_VERIFIED)
				.addContext("userId", user.getId());
		}
	}

	private School findSchoolByEmailDomain(String email) {
		String domain = extractDomain(email);
		return schoolRepository.findByEmailDomain(domain)
			.orElseThrow(() -> new BusinessException(SchoolErrorCode.NOT_FOUND).addContext("email", email));
	}

	private static String extractDomain(String email) {
		int at = email.indexOf('@');
		if (at < 0 || at == email.length() - 1) {
			throw new BusinessException(SchoolErrorCode.NOT_FOUND).addContext("email", email);
		}
		return email.substring(at + 1);
	}

	private User findUser(Long userId) {
		return userRepository.findByIdWithAssociations(userId)
			.orElseThrow(() -> new BusinessException(UserErrorCode.NOT_FOUND).addContext("userId", userId));
	}

	private static String generateCode() {
		return "%06d".formatted(RANDOM.nextInt(1_000_000));
	}

	private static String mailSubject() {
		return "[모집] 학교 인증번호";
	}

	private static String mailBody(String schoolName, String code) {
		return "%s 메일로 학교 인증을 요청하셨습니다. 인증번호: %s (5분 안에 입력해주세요)"
			.formatted(schoolName, code);
	}

}
