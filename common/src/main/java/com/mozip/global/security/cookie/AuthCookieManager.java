package com.mozip.global.security.cookie;

import java.time.Duration;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.WebUtils;

import com.mozip.global.security.jwt.JwtProperties;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AuthCookieManager {

	public static final String ACCESS_TOKEN_COOKIE = "access_token";
	public static final String REFRESH_TOKEN_COOKIE = "refresh_token";

	private static final String ACCESS_TOKEN_PATH = "/";
	// 리프레시 토큰 쿠키는 /auth 요청에만 전송
	private static final String REFRESH_TOKEN_PATH = "/auth";

	private final AuthCookieProperties cookieProperties;
	private final JwtProperties jwtProperties;

	public void addTokenCookies(HttpServletResponse response, String accessToken, String refreshToken) {
		addCookie(response, ACCESS_TOKEN_COOKIE, accessToken, ACCESS_TOKEN_PATH,
			jwtProperties.accessTokenExpiration());
		addCookie(response, REFRESH_TOKEN_COOKIE, refreshToken, REFRESH_TOKEN_PATH,
			jwtProperties.refreshTokenExpiration());
	}

	// Max-Age=0 쿠키로 덮어써 삭제
	public void expireTokenCookies(HttpServletResponse response) {
		addCookie(response, ACCESS_TOKEN_COOKIE, "", ACCESS_TOKEN_PATH, Duration.ZERO);
		addCookie(response, REFRESH_TOKEN_COOKIE, "", REFRESH_TOKEN_PATH, Duration.ZERO);
	}

	public static Optional<String> readCookie(HttpServletRequest request, String name) {
		Cookie cookie = WebUtils.getCookie(request, name);
		return Optional.ofNullable(cookie)
			.map(Cookie::getValue)
			.filter(StringUtils::hasText);
	}

	private void addCookie(HttpServletResponse response, String name, String value, String path,
		Duration maxAge) {
		ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
			.httpOnly(true)
			.secure(cookieProperties.secure())
			.sameSite(cookieProperties.sameSite().getAttributeValue())
			.path(path)
			.maxAge(maxAge);
		if (StringUtils.hasText(cookieProperties.domain())) {
			builder.domain(cookieProperties.domain());
		}
		response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
	}

}
