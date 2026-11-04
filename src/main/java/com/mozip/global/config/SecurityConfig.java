package com.mozip.global.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.mozip.global.logging.TraceIdFilter;
import com.mozip.global.security.ConditionalOnAuthBypass;
import com.mozip.global.security.CorsAllowedHeadersContributor;
import com.mozip.global.security.HttpSecurityDefaults;

import lombok.RequiredArgsConstructor;

// 운영용 보안 설정 (인증 우회 체인은 devtools.authbypass.AuthBypassSecurityConfig)
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

	private final HttpSecurityDefaults httpSecurityDefaults;
	private final CorsProperties corsProperties;
	private final List<CorsAllowedHeadersContributor> corsAllowedHeadersContributors;

	@Bean
	@ConditionalOnAuthBypass(enabled = false)
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return httpSecurityDefaults.apply(http)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpSecurityDefaults.PUBLIC_URLS).permitAll()
				.anyRequest().authenticated())
			.build();
	}

	// auth.cors.allowed-origins에 등록된 주소만 쿠키 포함 요청 허용
	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(corsProperties.allowedOrigins().stream()
			.filter(StringUtils::hasText)
			.map(String::trim)
			.toList());
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(allowedHeaders());
		configuration.setExposedHeaders(List.of(TraceIdFilter.TRACE_ID_HEADER));
		configuration.setAllowCredentials(true);
		configuration.setMaxAge(Duration.ofHours(1));

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

	private List<String> allowedHeaders() {
		List<String> headers = new ArrayList<>(List.of(HttpHeaders.CONTENT_TYPE, TraceIdFilter.TRACE_ID_HEADER));
		corsAllowedHeadersContributors.forEach(contributor -> headers.addAll(contributor.allowedHeaders()));
		return headers;
	}

}
