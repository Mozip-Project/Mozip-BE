package com.mozip.domain.message.dto;

import com.mozip.domain.application.entity.Application;
import com.mozip.domain.message.entity.MessageDelivery;

// 결과 안내를 받는 지원자 한 명. delivery가 null이면 아직 보내지 않았거나 이메일 경고로 빠진 것
public record Recipient(Application application, String email, EmailWarning emailWarning, String suggestedDomain,
	MessageDelivery delivery) {
}
