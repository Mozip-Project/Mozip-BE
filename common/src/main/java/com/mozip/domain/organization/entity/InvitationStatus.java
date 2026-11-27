package com.mozip.domain.organization.entity;

// 개인 초대 상태. 만료는 상태가 아니라 expiresAt으로 판단
public enum InvitationStatus {
	PENDING,
	ACCEPTED,
	DECLINED,
	CANCELED
}
