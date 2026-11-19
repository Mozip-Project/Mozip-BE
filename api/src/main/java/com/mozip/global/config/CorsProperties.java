package com.mozip.global.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

// application.yml의 auth.cors.* 설정
@ConfigurationProperties("auth.cors")
public record CorsProperties(
	@DefaultValue List<String> allowedOrigins
) {

	// 환경변수로 "a, b" 형태로 들어와도 쓸 수 있게 공백과 빈 값을 정리한 목록
	public List<String> normalizedAllowedOrigins() {
		return allowedOrigins.stream()
			.filter(StringUtils::hasText)
			.map(String::trim)
			.toList();
	}

}
