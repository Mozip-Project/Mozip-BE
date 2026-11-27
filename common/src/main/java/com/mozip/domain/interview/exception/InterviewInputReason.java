package com.mozip.domain.interview.exception;

// COMMON_400의 상황 구분. 면접 가능 시간 재선택에서 씀
public enum InterviewInputReason {

	// 면접 없는 공고의 지원서임
	NO_INTERVIEW,
	// 면접 가능 시간을 하나도 고르지 않음
	AVAILABLE_TIMES_REQUIRED

}
