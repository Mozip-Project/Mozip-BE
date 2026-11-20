package com.mozip.domain.organization.exception;

// MEMBER_409의 상황 구분 (extensions.reason으로 응답해 프론트가 문구를 분기)
public enum MemberReason {
	// 대표자(본인)는 내보낼 수 없음. 대표자 인수로 역할을 넘긴 뒤에 가능
	REPRESENTATIVE
}
