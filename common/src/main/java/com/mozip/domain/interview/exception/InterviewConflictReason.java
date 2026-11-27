package com.mozip.domain.interview.exception;

// INTERVIEW_409의 상황 구분
public enum InterviewConflictReason {

	// 타임테이블이 확정된 뒤라 면접 가능 시간을 바꾸거나 타임테이블을 다시 생성할 수 없음
	TIMETABLE_CONFIRMED,
	// 이미 확정된 타임테이블을 다시 확정하려 함
	ALREADY_CONFIRMED,
	// 배정된 지원자가 한 명도 없어 확정할 수 없음
	NO_ASSIGNED_APPLICANT,
	// 면접관이 같은 시간의 다른 면접 칸에 이미 들어가 있음
	INTERVIEWER_TIME_OVERLAP

}
