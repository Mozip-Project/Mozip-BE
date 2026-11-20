package com.mozip.domain.organization.exception;

// MEMBER_409의 상황 구분 (extensions.reason으로 응답해 프론트가 문구를 분기)
public enum MemberReason {
	// 대표자(본인)는 내보낼 수 없음. 대표자 인수로 역할을 넘긴 뒤에 가능
	REPRESENTATIVE,
	// 초대하거나 수락하려는 회원이 이미 그 단체 운영진
	ALREADY_MEMBER,
	// 같은 연락처로 만료 전 대기 중인 초대가 있음
	ALREADY_INVITED,
	// 이미 수락/거절/취소된 초대나 대표자 인수 요청
	ALREADY_HANDLED,
	// 그 단체에 대기 중인 대표자 인수 요청이 이미 있음
	TRANSFER_IN_PROGRESS
}
