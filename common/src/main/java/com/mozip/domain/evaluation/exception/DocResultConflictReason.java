package com.mozip.domain.evaluation.exception;

// EVALUATION_409의 상황 구분
public enum DocResultConflictReason {

	// 공고가 서류 평가 단계(DOC_REVIEW)가 아님
	NOT_DOC_REVIEW,
	// 공고 단체 운영진 전원이 평가를 저장하기 전에 서류 결과를 지정하려 함
	NOT_ALL_SAVED

}
