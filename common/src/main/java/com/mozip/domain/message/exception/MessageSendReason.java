package com.mozip.domain.message.exception;

// MESSAGE_422의 상황 구분. 발송 조건을 못 채운 대상이 하나라도 있으면 아무 대상도 발송하지 않음
public enum MessageSendReason {

	// 그 대상의 초안이 없음
	MESSAGE_NOT_SAVED,
	// 제목 또는 본문이 비어 있음
	EMPTY_MESSAGE,
	// 받는 사람이 0명
	NO_RECIPIENTS,
	// 받는 사람 전원이 이메일 경고라 보낼 수 있는 사람이 없음
	NO_SENDABLE_RECIPIENTS

}
