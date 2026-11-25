package com.mozip.domain.evaluation.dto;

// 서류 평가 목록 탭. 작성 중(DRAFT)은 아직 저장 버튼을 누르지 않았으니 평가 전으로 본다
public enum DocEvaluationTab {
	ALL,
	NOT_EVALUATED,
	EVALUATED;

	public boolean includes(MyDocEvaluationStatus status) {
		return switch (this) {
			case ALL -> true;
			case NOT_EVALUATED -> status != MyDocEvaluationStatus.SAVED;
			case EVALUATED -> status == MyDocEvaluationStatus.SAVED;
		};
	}
}
