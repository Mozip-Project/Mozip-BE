package com.mozip.domain.organization.exception;

import org.springframework.http.HttpStatus;

import com.mozip.global.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrganizationErrorCode implements ErrorCode {

	// TODO(B 단체): B가 단체 도메인을 구현하면 이 enum 전체가 B 소유로 바뀌고, 더 자세한 코드가 추가될 수 있음.
	// 지금은 OrganizationAccessChecker 임시 구현과 publishPosting의 이용권 검사에 필요한 코드만 먼저 둠
	ORGANIZATION_NOT_FOUND(HttpStatus.NOT_FOUND, "ORG_404", "단체를 찾을 수 없습니다."),
	SUBSCRIPTION_REQUIRED(HttpStatus.PAYMENT_REQUIRED, "ORG_402", "단체 이용권이 없습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;

}
