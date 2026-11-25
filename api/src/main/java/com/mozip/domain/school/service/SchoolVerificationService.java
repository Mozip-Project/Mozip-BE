package com.mozip.domain.school.service;

import java.security.SecureRandom;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.domain.file.dto.FilePurpose;
import com.mozip.domain.mail.service.MailTaskPublisher;
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
import com.mozip.domain.user.exception.UserErrorCode;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.domain.user.service.UserService;
import com.mozip.global.common.ReviewStatus;
import com.mozip.global.config.S3Properties;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;

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
	private final UserService userService;
	private final MailTaskPublisher mailTaskPublisher;
	private final S3Properties s3Properties;

	// 단체 만들기, 학교 인증 화면 자동완성
	public List<School> search(String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return List.of();
		}
		return schoolRepository.findTop20ByNameContainingIgnoreCaseOrderByName(keyword.trim());
	}

	@Transactional
	public boolean requestSchoolEmailVerification(Long userId, String email) {
		User user = findUser(userId);
		requireNotVerified(user);
		School school = findSchoolByEmailDomain(email);
		if (codeRepository.isOnCooldown(email)) {
			throw new BusinessException(SchoolErrorCode.TOO_MANY_REQUESTS).addContext("email", email);
		}

		String code = generateCode();
		codeRepository.markRequested(email);
		codeRepository.saveCode(email, code);
		try {
			mailTaskPublisher.publishSendMail(email, mailSubject(),
				mailHtmlBody(school.getName(), code), mailTextBody(school.getName(), code));
		} catch (RuntimeException e) {
			// worker로 작업을 넘기지 못함. 다시 요청해야 하므로 방금 저장한 인증번호는 지움
			codeRepository.deleteCode(email);
			throw e;
		}
		return true;
	}

	@Transactional
	public Me verifySchoolEmail(Long userId, String email, String code) {
		User user = findUser(userId);
		requireNotVerified(user);
		School school = findSchoolByEmailDomain(email);

		String savedCode = codeRepository.findCode(email)
			.orElseThrow(() -> new BusinessException(SchoolErrorCode.INVALID_INPUT, SchoolInputReason.CODE_EXPIRED)
				.addContext("email", email));
		if (!savedCode.equals(code)) {
			codeRepository.recordWrongAttempt(email);
			throw new BusinessException(SchoolErrorCode.INVALID_INPUT, SchoolInputReason.CODE_MISMATCH)
				.addContext("email", email);
		}

		codeRepository.deleteCode(email);
		schoolVerificationRepository.save(SchoolVerification.createApprovedEmail(user, school, email));
		user.verifySchool(school);
		return userService.me(userId);
	}

	@Transactional
	public SchoolVerification submitEnrollmentCertificate(Long userId, Long schoolId, String fileKey) {
		User user = findUser(userId);
		requireNotVerified(user);
		if (schoolVerificationRepository.existsByUserIdAndStatus(userId, ReviewStatus.PENDING)) {
			throw new BusinessException(SchoolErrorCode.CONFLICT, SchoolConflictReason.PENDING_EXISTS)
				.addContext("userId", userId);
		}
		// 발급 당시 purpose와 다른 fileKey가 들어오는 것을 막음 (다른 purpose로 발급받은 키 재사용)
		if (!fileKey.startsWith(FilePurpose.ENROLLMENT_CERT.keyPrefix() + "/")) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT).addContext("fileKey", fileKey);
		}
		School school = schoolRepository.findById(schoolId)
			.orElseThrow(() -> new BusinessException(SchoolErrorCode.NOT_FOUND).addContext("schoolId", schoolId));

		String certificateUrl = s3Properties.cdnDomain() + "/" + fileKey;
		return schoolVerificationRepository.save(
			SchoolVerification.createPendingCertificate(user, school, certificateUrl));
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

	private static String mailHtmlBody(String schoolName, String code) {
		return """
			<p>%s 메일로 학교 인증을 요청하셨습니다.</p>
			<p>아래 인증번호를 10분 안에 입력해주세요.</p>
			<h2>%s</h2>
			<p>요청하지 않았다면 이 메일을 무시해주세요.</p>
			""".formatted(schoolName, code);
	}

	private static String mailTextBody(String schoolName, String code) {
		return "%s 메일로 학교 인증을 요청하셨습니다. 인증번호: %s (10분 안에 입력해주세요)"
			.formatted(schoolName, code);
	}

}
