package com.mozip.domain.user.exception;

import org.springframework.http.HttpStatus;

import com.mozip.global.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

	DUPLICATE_PHONE(HttpStatus.CONFLICT, "USER_001", "이미 가입된 휴대폰 번호입니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;

}
