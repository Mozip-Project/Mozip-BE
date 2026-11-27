package com.mozip.domain.interview.exception;

import org.springframework.http.HttpStatus;

import com.mozip.global.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InterviewErrorCode implements ErrorCode {

	NOT_FOUND(HttpStatus.NOT_FOUND, "INTERVIEW_404", "면접 칸을 찾을 수 없습니다."),
	// reason으로 상황 구분 (InterviewConflictReason)
	CONFLICT(HttpStatus.CONFLICT, "INTERVIEW_409", "처리할 수 없는 상태입니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;

}
