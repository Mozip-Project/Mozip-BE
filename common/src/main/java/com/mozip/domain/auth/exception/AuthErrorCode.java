package com.mozip.domain.auth.exception;

import org.springframework.http.HttpStatus;

import com.mozip.global.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 로그인 관련 에러 (토큰 검증 에러는 global.security.jwt의 TokenErrorCode)
@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

	// 없는 번호, 틀린 비밀번호 모두 해당 코드로 응답
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_001", "휴대폰 번호 또는 비밀번호가 일치하지 않습니다."),
	INACTIVE_USER(HttpStatus.FORBIDDEN, "AUTH_005", "이용할 수 없는 계정입니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;

}
