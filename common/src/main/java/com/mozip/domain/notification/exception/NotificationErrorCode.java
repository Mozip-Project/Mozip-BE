package com.mozip.domain.notification.exception;

import org.springframework.http.HttpStatus;

import com.mozip.global.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationErrorCode implements ErrorCode {

	// 요청한 id 중 본인 알림이 아닌 게 섞여 있을 때 (조용히 무시하지 않고 거부)
	FORBIDDEN(HttpStatus.FORBIDDEN, "NOTIFICATION_403", "본인의 알림만 읽음 처리할 수 있습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;

}
