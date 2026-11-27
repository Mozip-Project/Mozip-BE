package com.mozip.domain.posting.service;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.entity.Report;
import com.mozip.domain.posting.entity.ReportReason;
import com.mozip.domain.posting.exception.PostingErrorCode;
import com.mozip.domain.posting.exception.ReportErrorCode;
import com.mozip.domain.posting.repository.PostingRepository;
import com.mozip.domain.posting.repository.ReportRepository;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

	private final ReportRepository reportRepository;
	private final PostingRepository postingRepository;
	private final UserRepository userRepository;

	@Transactional
	public boolean report(Long userId, Long postingId, ReportReason reason, String detail) {
		User user = findUser(userId);
		Posting posting = findPosting(postingId);
		if (reportRepository.existsByUserAndPosting(user, posting)) {
			throw new BusinessException(ReportErrorCode.CONFLICT).addContext("postingId", postingId);
		}
		try {
			reportRepository.save(Report.create(user, posting, reason, detail));
		} catch (DataIntegrityViolationException e) {
			// 동시에 같은 (user, posting)로 신고가 들어와 uk_reports_user_id_posting_id를 어긴 경우
			if (e.getCause() instanceof ConstraintViolationException cause
					&& "uk_reports_user_id_posting_id".equals(cause.getConstraintName())) {
				throw new BusinessException(ReportErrorCode.CONFLICT).addContext("postingId", postingId);
			}
			throw e;
		}
		return true;
	}

	// DRAFT는 아직 게시 전이라 지원자에게 없는 공고와 같게 취급
	private Posting findPosting(Long postingId) {
		return postingRepository.findPublishedById(postingId)
			.orElseThrow(() -> new BusinessException(PostingErrorCode.NOT_FOUND).addContext("postingId", postingId));
	}

	// 토큰의 회원이 DB에 없는 건 입력 실수가 아니라 세션이 더는 유효하지 않다는 뜻이라 401로 응답
	// FK 값만 필요해서 findById 대신 getReferenceById로 불필요한 컬럼 조회를 줄임
	private User findUser(Long userId) {
		if (!userRepository.existsById(userId)) {
			throw new BusinessException(CommonErrorCode.UNAUTHORIZED).addContext("userId", userId);
		}
		return userRepository.getReferenceById(userId);
	}

}
