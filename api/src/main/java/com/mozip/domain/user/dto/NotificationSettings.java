package com.mozip.domain.user.dto;

import com.mozip.domain.user.entity.User;

public record NotificationSettings(
	boolean notifyResult,
	boolean notifyScrap,
	boolean notifyOperator
) {

	public static NotificationSettings from(User user) {
		return new NotificationSettings(user.isNotifyResult(), user.isNotifyScrap(), user.isNotifyOperator());
	}

}
