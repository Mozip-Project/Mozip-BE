package com.mozip.domain.message.entity;

// DRAFT -> SENDING(발송 시작, PENDING 전달이 남음) -> SENT(전부 처리됨)
public enum MessageStatus {
	DRAFT,
	SENDING,
	SENT
}
