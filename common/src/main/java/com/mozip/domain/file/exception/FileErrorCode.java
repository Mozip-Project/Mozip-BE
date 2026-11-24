package com.mozip.domain.file.exception;

import org.springframework.http.HttpStatus;

import com.mozip.global.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FileErrorCode implements ErrorCode {

	FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "FILE_413", "허용된 파일 용량을 초과했습니다."),
	UNSUPPORTED_FILE_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "FILE_415", "허용하지 않는 파일 형식입니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;

}
