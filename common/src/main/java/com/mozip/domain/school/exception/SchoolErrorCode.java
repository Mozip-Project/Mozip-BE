package com.mozip.domain.school.exception;

import org.springframework.http.HttpStatus;

import com.mozip.global.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SchoolErrorCode implements ErrorCode {

	// 메일 도메인으로 학교를 못 찾음
	NOT_FOUND(HttpStatus.NOT_FOUND, "SCHOOL_404", "일치하는 학교를 찾을 수 없습니다."),
	// reason으로 구분 (SchoolInputReason)
	INVALID_INPUT(HttpStatus.BAD_REQUEST, "SCHOOL_400", "인증번호가 올바르지 않습니다."),
	// reason으로 구분 (SchoolConflictReason)
	CONFLICT(HttpStatus.CONFLICT, "SCHOOL_409", "이미 처리된 요청입니다."),
	// 짧은 시간에 인증 메일을 반복 요청함
	TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "SCHOOL_429", "잠시 후 다시 시도해주세요.");

	private final HttpStatus status;
	private final String code;
	private final String message;

}
