package com.mozip.domain.interview.exception;

// INTERVIEW_409의 상황 구분
public enum InterviewConflictReason {

	// 타임테이블이 확정된 뒤라 면접 가능 시간을 바꾸거나 타임테이블을 다시 생성할 수 없음
	TIMETABLE_CONFIRMED

}
