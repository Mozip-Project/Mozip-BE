package com.mozip.global.config;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.mozip.global.logging.TraceIdFilter;
import com.mozip.global.security.AuthBypassProperties;
import com.mozip.global.security.SecurityErrorHandler;
import com.mozip.global.security.jwt.JwtAuthenticationFilter;
import com.mozip.global.security.jwt.JwtProvider;
import com.mozip.global.security.mock.MockAuthenticationFilter;
import com.mozip.global.security.mock.MockUserRegistry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

	private final SecurityErrorHandler securityErrorHandler;
	private final JwtProvider jwtProvider;
	private final CorsProperties corsProperties;
	private final AuthBypassProperties authBypassProperties;
	private final Environment environment;
	// auth.bypass.enabled가 켜져 있을 때만 존재하는 빈
	private final ObjectProvider<MockUserRegistry> mockUserRegistry;
	private final ObjectMapper objectMapper;

	private static final String[] PUBLIC_URLS = {
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
		// 엔드포인트가 하나라 URL로 막지 않고 Query/Mutation별로 인증 검사
		"/graphql",
		"/graphiql",
		"/error"
	};

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		boolean bypassEnabled = isBypassEnabled();
		http
			// CSRF는 SameSite=Lax 쿠키로 방어 (다른 사이트 POST에는 쿠키 미전송)
			.csrf(AbstractHttpConfigurer::disable)
			.cors(Customizer.withDefaults())
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> {
				if (bypassEnabled) {
					auth.anyRequest().permitAll();
					return;
				}
				auth.requestMatchers(PUBLIC_URLS).permitAll()
					.anyRequest().authenticated();
			})
			.addFilterBefore(new JwtAuthenticationFilter(jwtProvider), UsernamePasswordAuthenticationFilter.class)
			.exceptionHandling(exception -> exception
				.authenticationEntryPoint(securityErrorHandler)
				.accessDeniedHandler(securityErrorHandler));
		if (bypassEnabled) {
			// 토큰 없는 요청은 X-Mock-Role 헤더의 개발용 회원으로 인증
			http.addFilterAfter(new MockAuthenticationFilter(mockUserRegistry.getObject(),
				authBypassProperties.defaultRole(), objectMapper), JwtAuthenticationFilter.class);
		}
		return http.build();
	}

	// auth.bypass.allowed-profiles에 없는 프로필에서 켜면 앱 시작 실패
	// 우회 중에는 MethodSecurityConfig도 로드되지 않음
	private boolean isBypassEnabled() {
		if (!authBypassProperties.isActive(environment)) {
			return false;
		}
		log.warn("인증 우회가 켜져 있습니다. 모든 요청이 인증, 권한 검사 없이 허용됩니다. (프로필: {})",
			List.of(environment.getActiveProfiles()));
		return true;
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
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
		// X-Mock-Role은 인증 우회 중에만 사용
		configuration.setAllowedHeaders(List.of(HttpHeaders.CONTENT_TYPE, TraceIdFilter.TRACE_ID_HEADER,
			MockAuthenticationFilter.MOCK_ROLE_HEADER));
		configuration.setExposedHeaders(List.of(TraceIdFilter.TRACE_ID_HEADER));
		configuration.setAllowCredentials(true);
		configuration.setMaxAge(Duration.ofHours(1));

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

}
