package com.mozip.domain.application.exception;

import org.springframework.http.HttpStatus;

import com.mozip.global.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ApplicationErrorCode implements ErrorCode {

	NOT_FOUND(HttpStatus.NOT_FOUND, "APPLICATION_404", "지원서를 찾을 수 없습니다."),
	// reason으로 상황 구분 (ApplicationConflictReason)
	CONFLICT(HttpStatus.CONFLICT, "APPLICATION_409", "처리할 수 없는 상태입니다."),
	// 제출 시 필수 문항 누락·글자 수·선택지·첨부 개수 위반. fieldErrors에 questionId별 메시지가 실림
	INVALID_INPUT(HttpStatus.BAD_REQUEST, "APPLICATION_400", "지원서 내용을 확인해주세요.");

	private final HttpStatus status;
	private final String code;
	private final String message;

}
