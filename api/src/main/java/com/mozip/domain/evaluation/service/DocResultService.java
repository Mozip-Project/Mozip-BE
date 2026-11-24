package com.mozip.domain.evaluation.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.domain.application.entity.Application;
import com.mozip.domain.application.entity.DocResult;
import com.mozip.domain.application.repository.ApplicationRepository;
import com.mozip.domain.evaluation.dto.ConfirmDocResultPayload;
import com.mozip.domain.evaluation.dto.DocResultSummary;
import com.mozip.domain.evaluation.dto.SetDocResultPayload;
import com.mozip.domain.evaluation.exception.DocResultConfirmReason;
import com.mozip.domain.evaluation.exception.DocResultConflictReason;
import com.mozip.domain.evaluation.exception.EvaluationErrorCode;
import com.mozip.domain.organization.service.OrganizationAccessChecker;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.entity.PostingStatus;
import com.mozip.domain.posting.exception.PostingErrorCode;
import com.mozip.domain.posting.repository.PostingRepository;
import com.mozip.global.config.graphql.LocalDateTimeScalar;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;

import lombok.RequiredArgsConstructor;

// 서류 결과 지정과 확정
// 두 메서드 모두 공고 행을 잠그고 시작해서, 확정 검사가 끝난 뒤에 결과가 바뀌거나 확정된 뒤에 결과가 들어가는 일이 없음
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocResultService {

	private final PostingRepository postingRepository;
	private final ApplicationRepository applicationRepository;
	private final OrganizationAccessChecker organizationAccessChecker;
	private final DocEvaluationSummaryService docEvaluationSummaryService;

	// 지원자 한 명의 서류 결과 지정. 같은 값을 다시 보내도 그대로 성공
	@Transactional
	public SetDocResultPayload setDocResult(Long userId, Long applicationId, DocResult result) {
		Application application = applicationRepository.findById(applicationId)
			.orElseThrow(() -> applicationNotFound(applicationId));
		// 지원서의 posting은 아직 프록시라 id만 꺼내고, 잠금 조회로 최신 값을 읽음
		Posting posting = postingRepository.findByIdWithOrganizationForUpdate(application.getPosting().getId())
			.orElseThrow(() -> applicationNotFound(applicationId));
		requireOperator(userId, posting);

		if (application.isDraft()) {
			throw applicationNotFound(applicationId);
		}
		if (result == null || result == DocResult.PENDING) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT)
				.addContext("applicationId", applicationId)
				.addContext("result", result);
		}
		if (posting.isDocResultConfirmed()) {
			throw resultConfirmed(posting.getId());
		}
		if (posting.getStatus() != PostingStatus.DOC_REVIEW) {
			throw new BusinessException(EvaluationErrorCode.RESULT_NOT_SELECTABLE,
				DocResultConflictReason.NOT_DOC_REVIEW)
				.addContext("applicationId", applicationId)
				.addContext("postingStatus", posting.getStatus());
		}
		if (!docEvaluationSummaryService.isEvaluationComplete(posting, application)) {
			throw new BusinessException(EvaluationErrorCode.RESULT_NOT_SELECTABLE,
				DocResultConflictReason.NOT_ALL_SAVED)
				.addContext("applicationId", applicationId);
		}

		application.decideDocResult(result);
		return new SetDocResultPayload(application, application.getDocResult(),
			docEvaluationSummaryService.summarize(posting));
	}

	// 서류 결과 확정. 조건 검사를 통과하면 조건부 UPDATE로 확정 시각을 채움
	@Transactional
	public ConfirmDocResultPayload confirmDocResult(Long userId, Long postingId) {
		Posting posting = postingRepository.findByIdWithOrganizationForUpdate(postingId)
			.orElseThrow(() -> new BusinessException(PostingErrorCode.NOT_FOUND).addContext("postingId", postingId));
		requireOperator(userId, posting);
		if (posting.isDocResultConfirmed()) {
			throw resultConfirmed(postingId);
		}
		// 모집 중에 확정하면 그 뒤에 제출된 지원서가 평가도 결과도 없이 남으므로 서류 평가 단계에서만 받음
		if (posting.getStatus() != PostingStatus.DOC_REVIEW) {
			throw new BusinessException(EvaluationErrorCode.RESULT_NOT_CONFIRMABLE,
				DocResultConfirmReason.NOT_DOC_REVIEW)
				.addContext("postingId", postingId)
				.addContext("postingStatus", posting.getStatus());
		}
		requireConfirmable(postingId, docEvaluationSummaryService.summarize(posting));

		// 잠금으로 이미 한 번에 하나만 들어오지만, 확정이 두 번 기록되지 않는다는 보장은 UPDATE 조건에 둠
		int updated = postingRepository.confirmDocResult(postingId, LocalDateTime.now(LocalDateTimeScalar.ZONE));
		if (updated == 0) {
			throw resultConfirmed(postingId);
		}
		// UPDATE가 영속성 컨텍스트를 비우므로 확정 시각이 들어간 공고를 다시 읽음
		Posting confirmed = postingRepository.findByIdWithOrganization(postingId)
			.orElseThrow(() -> new BusinessException(PostingErrorCode.NOT_FOUND).addContext("postingId", postingId));
		return new ConfirmDocResultPayload(confirmed, docEvaluationSummaryService.summarize(confirmed));
	}

	// 여러 조건에 걸리면 DocResultConfirmReason에 적힌 순서대로 하나만 알려줌 (NOT_DOC_REVIEW는 호출 전에 검사)
	private void requireConfirmable(Long postingId, DocResultSummary summary) {
		DocResultConfirmReason reason = null;
		if (summary.totalCount() == 0) {
			reason = DocResultConfirmReason.NO_APPLICANTS;
		} else if (!summary.allEvaluated()) {
			reason = DocResultConfirmReason.NOT_ALL_EVALUATED;
		} else if (summary.pendingCount() > 0) {
			reason = DocResultConfirmReason.RESULT_NOT_SELECTED;
		} else if (summary.holdCount() > 0) {
			reason = DocResultConfirmReason.HOLD_REMAINING;
		}
		if (reason != null) {
			throw new BusinessException(EvaluationErrorCode.RESULT_NOT_CONFIRMABLE, reason)
				.addContext("postingId", postingId)
				.addContext("totalCount", summary.totalCount())
				.addContext("evaluatedCount", summary.evaluatedCount())
				.addContext("pendingCount", summary.pendingCount())
				.addContext("holdCount", summary.holdCount());
		}
	}

	private void requireOperator(Long userId, Posting posting) {
		Long organizationId = posting.getOrganization().getId();
		if (!organizationAccessChecker.isOperatorOf(userId, organizationId)) {
			throw new BusinessException(CommonErrorCode.FORBIDDEN)
				.addContext("userId", userId)
				.addContext("organizationId", organizationId);
		}
	}

	private static BusinessException applicationNotFound(Long applicationId) {
		return new BusinessException(EvaluationErrorCode.APPLICATION_NOT_FOUND)
			.addContext("applicationId", applicationId);
	}

	private static BusinessException resultConfirmed(Long postingId) {
		return new BusinessException(EvaluationErrorCode.RESULT_CONFIRMED).addContext("postingId", postingId);
	}

}
