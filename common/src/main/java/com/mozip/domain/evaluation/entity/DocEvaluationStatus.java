package com.mozip.domain.evaluation.entity;

// 서류 평가 1건의 저장 상태. 평가가 아예 없는 경우(NONE)는 행이 없는 것으로 표현
public enum DocEvaluationStatus {

	// 자동 저장만 된 작성 중, 목록에서는 평가 전으로 셈
	DRAFT,
	// 저장 버튼으로 제출됨
	SAVED

}
