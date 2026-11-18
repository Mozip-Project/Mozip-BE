package com.mozip.domain.organization.exception;

// 단체 생성 입력의 필드 간 규칙 위반 (COMMON_400의 reason으로 응답해 프론트가 어느 필드인지 분기)
public enum OrganizationInputReason {
	COUNCIL_LEVEL_REQUIRED,
	COUNCIL_LEVEL_NOT_ALLOWED,
	SCHOOL_REQUIRED,
	SCHOOL_NOT_ALLOWED,
	SCHOOL_NOT_FOUND
}
