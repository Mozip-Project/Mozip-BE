package com.mozip.domain.mail.exception;

import org.springframework.http.HttpStatus;

import com.mozip.global.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MailErrorCode implements ErrorCode {

	// SES 일시 장애, 발송 한도 초과 등 재시도로 해결될 수 있는 실패
	SEND_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "MAIL_503", "메일 발송에 실패했습니다. 잠시 후 다시 시도해주세요.");

	private final HttpStatus status;
	private final String code;
	private final String message;

}
