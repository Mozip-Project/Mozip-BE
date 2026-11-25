package com.mozip.domain.organization.exception;

import org.springframework.http.HttpStatus;

import com.mozip.global.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrganizationErrorCode implements ErrorCode {

	// 이용권 없는 단체의 유료 기능 (공고 게시 등) 호출
	SUBSCRIPTION_REQUIRED(HttpStatus.PAYMENT_REQUIRED, "ORG_402", "단체 이용권이 없습니다."),
	ORGANIZATION_NOT_FOUND(HttpStatus.NOT_FOUND, "ORG_404", "단체를 찾을 수 없습니다."),
	// reason으로 상황 구분 (OrganizationConflictReason)
	CONFLICT(HttpStatus.CONFLICT, "ORG_409", "처리할 수 없는 상태입니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;

}
