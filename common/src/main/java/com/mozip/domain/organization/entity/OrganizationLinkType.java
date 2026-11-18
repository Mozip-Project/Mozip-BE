package com.mozip.domain.organization.entity;

import java.util.Locale;

// 프로필 폼에 타입 선택 UI가 없어 서버가 URL 호스트로 추정
public enum OrganizationLinkType {
	INSTAGRAM,
	WEBSITE,
	YOUTUBE,
	ETC;

	public static OrganizationLinkType fromHost(String host) {
		String lower = host.toLowerCase(Locale.ROOT);
		if (lower.equals("instagram.com") || lower.endsWith(".instagram.com")) {
			return INSTAGRAM;
		}
		if (lower.equals("youtube.com") || lower.endsWith(".youtube.com") || lower.equals("youtu.be")) {
			return YOUTUBE;
		}
		return WEBSITE;
	}

}
