package com.mozip.global.security;

import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.CorsFilter;

import com.mozip.global.common.response.ApiResponseWriter;
import com.mozip.global.config.CorsProperties;
import com.mozip.global.security.jwt.JwtAuthenticationFilter;
import com.mozip.global.security.jwt.JwtProvider;

import lombok.RequiredArgsConstructor;

// 운영 체인과 인증 우회 체인이 공통으로 쓰는 설정 (URL 인가 규칙만 각 체인에서 정함)
@Component
@RequiredArgsConstructor
public class HttpSecurityDefaults {

	// 로그인 없이 호출할 수 있는 URL
	public static final String[] PUBLIC_URLS = {
		"/swagger-ui.html",
		"/swagger-ui/**",
		"/v3/api-docs/**",
		"/actuator/health",
		"/api/health",
		// 인증 API (로그인 전, 토큰 만료 상태에서 호출)
		"/auth/signup",
		"/auth/login",
		"/auth/reissue",
		"/auth/logout",
		// 엔드포인트가 하나라 URL로 막지 않고 GraphqlAuthInstrumentation이 필드별로 검사
		"/graphql",
		"/graphiql",
		"/error"
	};

	private final SecurityErrorHandler securityErrorHandler;
	private final JwtProvider jwtProvider;
	private final CorsProperties corsProperties;
	private final ApiResponseWriter apiResponseWriter;

	public HttpSecurity apply(HttpSecurity http) throws Exception {
		return http
			// CSRF 토큰 대신 SameSite 쿠키(다른 사이트 POST에는 쿠키 미전송)와 OriginVerificationFilter로 방어
			.csrf(AbstractHttpConfigurer::disable)
			.cors(Customizer.withDefaults())
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.addFilterAfter(new OriginVerificationFilter(corsProperties.normalizedAllowedOrigins(), apiResponseWriter),
				CorsFilter.class)
			.addFilterBefore(new JwtAuthenticationFilter(jwtProvider), UsernamePasswordAuthenticationFilter.class)
			.exceptionHandling(exception -> exception
				.authenticationEntryPoint(securityErrorHandler)
				.accessDeniedHandler(securityErrorHandler));
	}

}
