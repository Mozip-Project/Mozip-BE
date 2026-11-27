package com.mozip.domain.school.entity;

// 학교 인증 메일이 worker에서 실제로 발송됐는지 상태. Redis에만 짧게 저장하고 DB에는 남기지 않음
public enum SchoolEmailVerificationStatus {
	PENDING,
	SENT,
	FAILED
}
