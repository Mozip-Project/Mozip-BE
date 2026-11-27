package com.mozip.domain.interview.exception;

// COMMON_400의 상황 구분. 면접 가능 시간 재선택과 타임테이블에서 씀
public enum InterviewInputReason {

	// 면접 없는 공고임
	NO_INTERVIEW,
	// 면접 가능 시간을 하나도 고르지 않음
	AVAILABLE_TIMES_REQUIRED,
	// 공고의 면접 설정(면접 시간, 쉬는 시간, 정원, 동시 진행 수)이 다 채워지지 않음
	INTERVIEW_SETTING_REQUIRED

}
