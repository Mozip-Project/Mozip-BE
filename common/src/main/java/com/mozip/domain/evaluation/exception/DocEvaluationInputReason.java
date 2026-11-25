package com.mozip.domain.evaluation.exception;

// 서류 평가 저장 입력 위반 (COMMON_400의 reason으로 응답해 프론트가 어느 필드인지 분기)
public enum DocEvaluationInputReason {
	// 저장 버튼인데 의견이 없음
	OPINION_REQUIRED,
	// 저장 버튼인데 점수를 안 매긴 문항이 있음
	SCORE_REQUIRED,
	// 점수가 1~5점이 아님
	INVALID_SCORE,
	// 메모가 300자를 넘음
	MEMO_TOO_LONG,
	// 이 지원서에 적용되는 문항이 아님
	QUESTION_NOT_IN_POSTING
}
