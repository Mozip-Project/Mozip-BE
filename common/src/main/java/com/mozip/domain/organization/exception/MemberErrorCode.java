package com.mozip.domain.organization.exception;

import org.springframework.http.HttpStatus;

import com.mozip.global.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MemberErrorCode implements ErrorCode {

	MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER_404", "운영진을 찾을 수 없습니다."),
	MEMBER_CONFLICT(HttpStatus.CONFLICT, "MEMBER_409", "현재 운영진 상태에서는 처리할 수 없는 요청입니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;

}
