package com.mozip.domain.evaluation.exception;

// EVALUATION_422의 상황 구분. 여러 개에 걸리면 아래 순서에서 먼저 나오는 것 하나만 내려감
public enum DocResultConfirmReason {

	// 제출된 지원서가 한 건도 없음
	NO_APPLICANTS,
	// 운영진 전원이 평가를 저장하지 않은 지원자가 남아 있음
	NOT_ALL_EVALUATED,
	// 서류 결과를 고르지 않은(PENDING) 지원자가 남아 있음
	RESULT_NOT_SELECTED,
	// 보류로 둔 지원자가 남아 있음
	HOLD_REMAINING

}
