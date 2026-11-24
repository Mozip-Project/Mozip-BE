package com.mozip.domain.message.dto;

// 받는 사람 주소에 대한 서버 판단. 경고가 있는 지원자는 발송 대상에서 빠짐
public enum EmailWarning {
	// 흔한 메일 도메인이나 학교 메일 도메인과 편집 거리 1
	SUSPICIOUS_DOMAIN,
	// 주소가 없거나 local@domain 형식이 아님
	MISSING
}
