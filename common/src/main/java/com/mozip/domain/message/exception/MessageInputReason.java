package com.mozip.domain.message.exception;

// COMMON_400의 상황 구분. 메시지 저장과 발송 인자가 규칙에 맞지 않을 때 씀
public enum MessageInputReason {

	// 아직 지원하지 않는 type (지금은 DOC_RESULT만 가능)
	UNSUPPORTED_TYPE,
	// type과 맞지 않는 target (DOC_RESULT에 ALL)
	INVALID_TARGET,
	SUBJECT_REQUIRED,
	SUBJECT_TOO_LONG,
	BODY_REQUIRED,
	BODY_TOO_LONG,
	// sendMessages의 targets가 비어 있음
	TARGETS_REQUIRED,
	// sendMessages의 targets에 같은 값이 두 번 들어옴
	DUPLICATE_TARGET

}
