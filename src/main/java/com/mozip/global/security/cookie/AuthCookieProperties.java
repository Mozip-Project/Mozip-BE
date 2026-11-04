package com.mozip.global.security.cookie;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// application.yml의 auth.cookie.* 설정
@ConfigurationProperties("auth.cookie")
public record AuthCookieProperties(
	@DefaultValue("true") boolean secure,
	@DefaultValue("LAX") CookieSameSite sameSite,
	String domain
) {
}
