package com.mozip.domain.posting.exception;

import org.springframework.http.HttpStatus;

import com.mozip.global.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PostingErrorCode implements ErrorCode {

	NOT_FOUND(HttpStatus.NOT_FOUND, "POSTING_404", "공고를 찾을 수 없습니다."),
	// reason으로 상황 구분 (PostingConflictReason: NOT_DRAFT, ALREADY_PUBLISHED)
	CONFLICT(HttpStatus.CONFLICT, "POSTING_409", "처리할 수 없는 상태입니다."),
	// reason으로 어느 단계가 비어 있는지 구분 (PostingPublishReason), fieldErrors에도 같이 실림
	INVALID_INPUT(HttpStatus.BAD_REQUEST, "POSTING_400", "게시에 필요한 정보가 비어 있습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;

}
