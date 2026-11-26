package com.mozip.domain.school.service;

import java.security.SecureRandom;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

	// 가입 폼의 나머지 입력(전화번호, 비밀번호 등)을 채우기 전에 코드가 맞는지 확인. 로그인 불필요(@public).
	// 이미 확인을 마친 메일로 다시 부르면 에러(SCHOOL_409, EMAIL_ALREADY_CONFIRMED)를 던짐.
	// 그 외 코드 불일치/만료/모르는 도메인은 에러 없이 false만 반환함(틀린 코드 기록 같은 부수효과는 남음).
	// 성공하면 코드는 지우고 confirmed로 표시(10분), 실제 가입(verifyForSignup)은 이 표시만 확인함
	@Transactional
	public boolean verifySchoolEmail(String email, String code) {
		if (codeRepository.isConfirmed(email)) {
			throw new BusinessException(SchoolErrorCode.CONFLICT, SchoolConflictReason.EMAIL_ALREADY_CONFIRMED)
				.addContext("email", email);
		}
		try {
			consumeValidCode(email, code);
		} catch (BusinessException e) {
			return false;
		}
		codeRepository.markConfirmed(email);
		return true;
	}

	// 회원가입 시 학교 메일 인증 여부만 확인(읽기 전용, Redis confirmed 표시를 지우지 않음). verifySchoolEmail로
	// confirmed 표시가 남아 있어야 하고(10분 안에 가입해야 함), 없으면 SCHOOL_400(EMAIL_NOT_VERIFIED).
	// 여기서 지우지 않는 이유: User 저장(DB)과 Redis는 같은 트랜잭션으로 안 묶여서, 저장이 실패해도 Redis
	// 삭제는 롤백되지 않음. 실제로 가입이 끝난 뒤(completeSignupVerification)에만 지워서 실패 시 재시도 가능하게 함
	public School verifyForSignup(String email) {
		if (!codeRepository.isConfirmed(email)) {
			throw new BusinessException(SchoolErrorCode.INVALID_INPUT, SchoolInputReason.EMAIL_NOT_VERIFIED)
				.addContext("email", email);
		}
		return findSchoolByEmailDomain(email);
	}

	// 가입으로 생성된 User에 학교 인증 완료를 반영. confirmed 삭제는 DB 커밋 후로 미뤄서(afterCommit), 이 메서드 안에서
	// SchoolVerification 저장이 실패해 트랜잭션이 롤백돼도 Redis만 먼저 지워지는 일이 없게 함.
	// User 저장 전에는 school_verifications.user_id를 채울 수 없어 verifyForSignup과는 분리돼 있음
	@Transactional
	public void completeSignupVerification(User user, School school, String email) {
		schoolVerificationRepository.save(SchoolVerification.createApprovedEmail(user, school, email));
		user.verifySchool(school);
		runAfterCommit(() -> codeRepository.deleteConfirmed(email));
	}

	// 트랜잭션이 실제로 커밋된 뒤에만 Redis를 건드림. @Transactional 메서드 안에서 호출되는 동안은 항상
	// 트랜잭션이 활성 상태라 동기화가 등록돼 있지만, 혹시 트랜잭션 밖에서 불리면 바로 실행함
	private void runAfterCommit(Runnable action) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			action.run();
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				action.run();
			}
		});
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
