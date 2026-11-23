package com.mozip.domain.application.exception;

// COMMON_400의 상황 구분. 다른 공고의 것을 참조하거나 열려 있지 않은 면접 시간을 선택했을 때 씀
public enum ApplicationInputReason {

	// 다른 공고의 포지션을 선택함
	POSITION_FROM_ANOTHER_POSTING,
	// 다른 공고의 문항으로 답변함
	QUESTION_FROM_ANOTHER_POSTING,
	// 선택한 포지션의 문항이 아닌 문항에 답변함
	QUESTION_NOT_FOR_POSITION,
	// availableTimes가 열려 있는 면접 시간 블록의 시작 시각이 아님
	AVAILABLE_TIME_NOT_OPEN

}
