package com.mozip.domain.organization.exception;

// 단체 생성/프로필 수정 입력의 필드 간 규칙 위반 (COMMON_400의 reason으로 응답해 프론트가 어느 필드인지 분기)
public enum OrganizationInputReason {
	COUNCIL_LEVEL_REQUIRED,
	COUNCIL_LEVEL_NOT_ALLOWED,
	SCHOOL_REQUIRED,
	SCHOOL_NOT_ALLOWED,
	SCHOOL_NOT_FOUND,
	// 학생회가 아닌 단체는 활동 분야 1개 이상, 학생회는 활동 분야 없음
	FIELDS_REQUIRED,
	FIELDS_NOT_ALLOWED,
	// 호스트를 읽을 수 없는 링크 (예: 공백, 스킴만 있는 문자열)
	LINK_URL_INVALID
}
