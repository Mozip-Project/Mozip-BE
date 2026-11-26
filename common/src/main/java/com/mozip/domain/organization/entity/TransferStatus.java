package com.mozip.domain.organization.entity;

// 대표자 인수 요청 상태. CANCELED는 요청자나 대상이 단체를 나가 무효가 된 경우
public enum TransferStatus {
	PENDING,
	APPROVED,
	REJECTED,
	CANCELED
}
