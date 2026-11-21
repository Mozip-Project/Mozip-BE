package com.mozip.domain.application.exception;

// APPLICATION_409의 상황 구분
public enum ApplicationConflictReason {

	// 이미 제출된(SUBMITTED) 지원서를 다시 저장하거나 제출하려 함
	ALREADY_SUBMITTED

}
