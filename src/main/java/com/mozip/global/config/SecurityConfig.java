package com.mozip.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import com.mozip.global.security.SecurityErrorHandler;

import lombok.RequiredArgsConstructor;

/**
 * 기본 보안 설정 · JWT 필터는 인증 기능 구현 때 추가
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

	private final SecurityErrorHandler securityErrorHandler;

	private static final String[] PUBLIC_URLS = {
		"/swagger-ui.html",
		"/swagger-ui/**",
		"/v3/api-docs/**",
		"/actuator/health",
		"/api/health",
		"/error"
	};

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.csrf(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(PUBLIC_URLS).permitAll()
				.anyRequest().authenticated())
			.exceptionHandling(exception -> exception
				.authenticationEntryPoint(securityErrorHandler)
				.accessDeniedHandler(securityErrorHandler));
		return http.build();
	}

}
