package com.mozip.domain.evaluation.exception;

import org.springframework.http.HttpStatus;

import com.mozip.global.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EvaluationErrorCode implements ErrorCode {

	// 지원서가 없거나 아직 제출 전이라 평가 대상이 아님
	APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "EVALUATION_404", "평가할 지원서를 찾을 수 없습니다."),
	// 서류 결과가 확정된 공고의 평가나 결과를 바꾸려 함
	RESULT_CONFIRMED(HttpStatus.LOCKED, "EVALUATION_423", "결과가 확정되어 더 이상 수정할 수 없습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;

}
