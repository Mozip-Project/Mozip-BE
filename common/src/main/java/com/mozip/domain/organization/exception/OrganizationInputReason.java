package com.mozip.domain.organization.exception;

// 단체 생성/프로필 수정 입력의 필드 간 규칙 위반, COMMON_400의 reason으로 응답
public enum OrganizationInputReason {
	COUNCIL_LEVEL_REQUIRED,
	COUNCIL_LEVEL_NOT_ALLOWED,
	SCHOOL_REQUIRED,
	SCHOOL_NOT_ALLOWED,
	SCHOOL_NOT_FOUND,
	// 학생회가 아닌 단체는 활동 분야 1개 이상, 학생회는 활동 분야 없음
	FIELDS_REQUIRED,
	FIELDS_NOT_ALLOWED,
	// http, https가 아니거나 500자를 넘거나 호스트가 없는 링크
	LINK_URL_INVALID
}
