package com.mozip.domain.message.exception;

import org.springframework.http.HttpStatus;

import com.mozip.global.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MessageErrorCode implements ErrorCode {

	// reason으로 상황 구분 (MessageConflictReason)
	CONFLICT(HttpStatus.CONFLICT, "MESSAGE_409", "처리할 수 없는 상태입니다."),
	// reason으로 어떤 발송 조건이 빠졌는지 구분 (MessageSendReason)
	NOT_SENDABLE(HttpStatus.UNPROCESSABLE_CONTENT, "MESSAGE_422", "발송 조건을 충족하지 않았습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;

}
