package com.mozip.domain.user.exception;

import org.springframework.http.HttpStatus;

import com.mozip.global.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

	DUPLICATE_PHONE(HttpStatus.CONFLICT, "USER_001", "이미 가입된 휴대폰 번호입니다."),
	// 지원서 제출 자격 미충족 (학교 인증 등). submitApplication에서 사용 (#69)
	INELIGIBLE(HttpStatus.FORBIDDEN, "USER_403", "지원 자격을 확인해주세요.");

	private final HttpStatus status;
	private final String code;
	private final String message;

}
