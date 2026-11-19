package com.mozip.global.security.cookie;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 토큰 쿠키에 허용하는 SameSite 값
// None은 다른 사이트의 POST에도 쿠키가 실려 CSRF 방어가 사라지므로 설정으로도 고를 수 없게 enum에서 제외
@Getter
@RequiredArgsConstructor
public enum CookieSameSite {

	STRICT("Strict"),
	LAX("Lax");

	private final String attributeValue;

}
