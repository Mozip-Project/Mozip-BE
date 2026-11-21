package com.mozip.domain.organization.exception;

// MEMBER_409의 상황 구분, extensions.reason으로 응답
public enum MemberReason {
	// 대표자 본인은 내보낼 수 없음
	REPRESENTATIVE,
	// 초대하거나 수락하려는 회원이 이미 그 단체 운영진
	ALREADY_MEMBER,
	// 같은 연락처로 만료 전 대기 중인 초대가 있음
	ALREADY_INVITED,
	// 이미 수락/거절/취소된 초대
	ALREADY_HANDLED
}
