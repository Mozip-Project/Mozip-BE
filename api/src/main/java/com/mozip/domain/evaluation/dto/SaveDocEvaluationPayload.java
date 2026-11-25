package com.mozip.domain.evaluation.dto;

import com.mozip.domain.evaluation.entity.DocEvaluation;

public record SaveDocEvaluationPayload(
	DocEvaluation evaluation,
	// 이 공고의 제출된 지원서 중 내 평가가 없거나 작성 중인 수
	int remainingCount,
	// 제출일 최신순으로 처음 나오는 미평가 지원서, 없으면 null
	Long nextUnevaluatedApplicationId
) {
}
