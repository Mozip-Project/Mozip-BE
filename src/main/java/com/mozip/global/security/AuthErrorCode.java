package com.mozip.global.security;

import org.springframework.http.HttpStatus;

import com.mozip.global.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

	// 없는 번호, 틀린 비밀번호 모두 해당 코드로 응답
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_001", "휴대폰 번호 또는 비밀번호가 일치하지 않습니다."),
	// 이 코드를 받으면 /auth/reissue 호출 후 원래 요청 재시도
	EXPIRED_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_002", "액세스 토큰이 만료되었습니다."),
	INVALID_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_003", "유효하지 않은 액세스 토큰입니다."),
	// 재발급 실패 (만료, 위조, 이미 사용됨, 로그아웃됨) 시 다시 로그인
	INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_004", "로그인이 만료되었습니다. 다시 로그인해 주세요."),
	INACTIVE_USER(HttpStatus.FORBIDDEN, "AUTH_005", "이용할 수 없는 계정입니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;

}
