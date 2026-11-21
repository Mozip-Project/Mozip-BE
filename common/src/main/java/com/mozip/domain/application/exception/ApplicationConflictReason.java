package com.mozip.domain.application.exception;

// APPLICATION_409의 상황 구분
public enum ApplicationConflictReason {

	// 이미 제출된(SUBMITTED) 지원서를 다시 저장하거나 제출하려 함
	ALREADY_SUBMITTED,
	// 같은 공고·회원으로 동시에 saveApplicationDraft가 들어와 지원서가 이미 막 생성됨. 다시 요청하면 그 지원서를 덮어씀
	CONCURRENT_SAVE

}
