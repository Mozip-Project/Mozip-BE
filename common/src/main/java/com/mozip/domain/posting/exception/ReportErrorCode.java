package com.mozip.domain.posting.exception;

import org.springframework.http.HttpStatus;

import com.mozip.global.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReportErrorCode implements ErrorCode {

	// 같은 회원이 같은 공고를 두 번 신고하려 함. 상황이 하나뿐이라 reason enum은 안 둠
	CONFLICT(HttpStatus.CONFLICT, "REPORT_409", "이미 신고한 공고입니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;

}
