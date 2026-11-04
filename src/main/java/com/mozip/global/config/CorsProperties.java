package com.mozip.global.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// application.yml의 auth.cors.* 설정
@ConfigurationProperties("auth.cors")
public record CorsProperties(
	@DefaultValue List<String> allowedOrigins
) {
}
