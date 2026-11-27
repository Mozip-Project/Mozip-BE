package com.mozip.domain.interview.exception;

// COMMON_400의 상황 구분. 면접 가능 시간 재선택과 타임테이블에서 씀
public enum InterviewInputReason {

	// 면접 없는 공고임
	NO_INTERVIEW,
	// 면접 가능 시간을 하나도 고르지 않음
	AVAILABLE_TIMES_REQUIRED,
	// 공고의 면접 설정(면접 시간, 쉬는 시간, 정원, 동시 진행 수)이 다 채워지지 않음
	INTERVIEW_SETTING_REQUIRED,
	// 면접 칸을 새로 추가하는데 시작 시각을 안 보냄
	START_AT_REQUIRED,
	// 새 면접 칸의 시작 시각이 열려 있는 블록의 시작 시각이 아님
	SLOT_NOT_OPEN,
	// 배정하려는 지원서가 이 공고의 서류 합격자가 아님
	APPLICANT_NOT_ELIGIBLE,
	// 면접관으로 넣으려는 운영진이 이 공고 단체 소속이 아님
	INTERVIEWER_NOT_MEMBER

}
