package com.mozip.domain.posting.service;

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
			throw new BusinessException(ReportErrorCode.CONFLICT).addContext("postingId", postingId);
		}
		return true;
	}

	private Posting findPosting(Long postingId) {
		return postingRepository.findById(postingId)
			.orElseThrow(() -> new BusinessException(PostingErrorCode.NOT_FOUND).addContext("postingId", postingId));
	}

	private User findUser(Long userId) {
		return userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(CommonErrorCode.INVALID_INPUT).addContext("userId", userId));
	}

}
