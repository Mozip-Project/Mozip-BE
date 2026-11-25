package com.mozip.domain.notification.dto;

public record UpdateNotificationSettingsInput(
	boolean notifyResult,
	boolean notifyScrap,
	boolean notifyOperator
) {
}
