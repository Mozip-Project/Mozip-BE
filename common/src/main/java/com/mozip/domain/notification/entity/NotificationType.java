package com.mozip.domain.notification.entity;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import com.mozip.domain.user.entity.Role;
import com.mozip.domain.user.entity.User;

// 알림 탭(지원자/운영진), 알림 설정 토글, 출처 라벨, 이동 화면을 type으로 정함 (user.graphqls의 NotificationType과 값을 맞춤)
public enum NotificationType {

	// 설정과 상관없이 항상 저장 (공고 공지는 운영진이 직접 보내는 공지라 끄면 안 됨)
	POSTING_NOTICE(Role.APPLICANT, null),
	DOC_RESULT(Role.APPLICANT, User::isNotifyResult),
	FINAL_RESULT(Role.APPLICANT, User::isNotifyResult),
	INTERVIEW_RESELECT(Role.APPLICANT, User::isNotifyResult),
	SCRAP_OPEN(Role.APPLICANT, User::isNotifyScrap),
	// 설정과 상관없이 항상 저장 (초대는 수락하지 않으면 사라지는 액션이라 끄면 안 됨)
	INVITATION(Role.OPERATOR, null),
	TRANSFER_REQUEST(Role.OPERATOR, User::isNotifyOperator),
	TRANSFER_APPROVED(Role.OPERATOR, User::isNotifyOperator),
	TRANSFER_REJECTED(Role.OPERATOR, User::isNotifyOperator),
	EVALUATION_REQUEST(Role.OPERATOR, User::isNotifyOperator),
	EVALUATION_DEADLINE(Role.OPERATOR, User::isNotifyOperator);

	private final Role tab;
	private final Predicate<User> setting;

	NotificationType(Role tab, Predicate<User> setting) {
		this.tab = tab;
		this.setting = setting;
	}

	public Role tab() {
		return tab;
	}

	public static List<NotificationType> byTab(Role tab) {
		return Arrays.stream(values()).filter(type -> type.tab == tab).toList();
	}

	// setting이 없으면(null) 알림 설정과 무관하게 항상 허용
	public boolean isAllowedFor(User user) {
		return setting == null || setting.test(user);
	}

}
