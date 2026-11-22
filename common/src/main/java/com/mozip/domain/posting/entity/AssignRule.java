package com.mozip.domain.posting.entity;

// 면접관 자동 배정 기준. 포지션 없는 모집은 자동 배정 없이 운영진이 직접 추가
public enum AssignRule {
	// 지원 포지션과 같은 팀 운영진
	SAME_TEAM,
	// 팀 상관없이 운영진 전체
	ALL_MEMBERS
}
