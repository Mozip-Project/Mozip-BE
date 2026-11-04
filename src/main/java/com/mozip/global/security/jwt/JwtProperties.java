package com.mozip.global.security.jwt;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

// application.yml을 바인딩
@ConfigurationProperties("jwt")
public record JwtProperties(
	String secret,
	Duration accessTokenExpiration,
	Duration refreshTokenExpiration
) {
}
