package com.mozip.domain.evaluation.dto;

import com.mozip.domain.evaluation.entity.DocEvaluation;

// 한 지원서에 대한 내 평가 상태. 평가 행이 없는 경우(NONE)까지 포함해서 엔티티의 DocEvaluationStatus와 따로 둔다
public enum MyDocEvaluationStatus {
	NONE,
	DRAFT,
	SAVED;

	// null = 아직 평가 행이 없음
	public static MyDocEvaluationStatus from(DocEvaluation evaluation) {
		if (evaluation == null) {
			return NONE;
		}
		return evaluation.isSaved() ? SAVED : DRAFT;
	}
}
