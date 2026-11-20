package com.mozip.domain.organization.exception;

import org.springframework.http.HttpStatus;

import com.mozip.global.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrganizationErrorCode implements ErrorCode {

	NOT_FOUND(HttpStatus.NOT_FOUND, "ORG_404", "단체를 찾을 수 없습니다."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "ORG_403", "해당 단체의 운영진만 수정할 수 있습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;

}
