package com.mozip.global.exception;

import org.springframework.http.HttpStatus;

// 도메인마다 이 인터페이스를 구현한 enum을 추가하여 사용 예정 (예: ClubErrorCode, AuthErrorCode)
public interface ErrorCode {

	HttpStatus getStatus();

	String getCode();

	String getMessage();

}
