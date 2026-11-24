package com.mozip.domain.user.dto;

// 알림 기능 구현 전까지는 항상 기본값(모두 켜짐)으로 응답
public record NotificationSettings(
	boolean notifyResult,
	boolean notifyScrap,
	boolean notifyOperator
) {

	public static NotificationSettings defaults() {
		return new NotificationSettings(true, true, true);
	}

}
