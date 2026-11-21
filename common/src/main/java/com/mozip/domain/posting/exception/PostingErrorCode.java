package com.mozip.domain.posting.exception;

import org.springframework.http.HttpStatus;

import com.mozip.global.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PostingErrorCode implements ErrorCode {

	NOT_FOUND(HttpStatus.NOT_FOUND, "POSTING_404", "공고를 찾을 수 없습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;

}
