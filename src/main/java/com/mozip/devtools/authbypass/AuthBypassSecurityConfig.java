package com.mozip.devtools.authbypass;

import java.util.Arrays;
import java.util.List;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import com.mozip.global.common.response.ApiResponseWriter;
import com.mozip.global.security.ConditionalOnAuthBypass;
import com.mozip.global.security.CorsAllowedHeadersContributor;
import com.mozip.global.security.HttpSecurityDefaults;
import com.mozip.global.security.jwt.JwtAuthenticationFilter;

import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import lombok.extern.slf4j.Slf4j;

// 인증 우회 중에만 로드되는 보안 설정 (운영 체인 대신 모든 요청 허용, X-Mock-Role 헤더로 인증)
// 허용되지 않은 프로필에서 켜면 생성 시점에 예외가 나서 앱이 시작되지 않음
@Slf4j
@Configuration
@ConditionalOnAuthBypass
public class AuthBypassSecurityConfig {

	private final HttpSecurityDefaults httpSecurityDefaults;
	private final AuthBypassProperties properties;
	private final MockUserRegistry mockUserRegistry;
	private final ApiResponseWriter apiResponseWriter;

	public AuthBypassSecurityConfig(HttpSecurityDefaults httpSecurityDefaults, AuthBypassProperties properties,
		MockUserRegistry mockUserRegistry, ApiResponseWriter apiResponseWriter, Environment environment) {
		properties.validateProfiles(environment);
		log.warn("인증 우회가 켜져 있습니다. 모든 요청이 인증, 권한 검사 없이 허용됩니다. (프로필: {})",
			List.of(environment.getActiveProfiles()));
		this.httpSecurityDefaults = httpSecurityDefaults;
		this.properties = properties;
		this.mockUserRegistry = mockUserRegistry;
		this.apiResponseWriter = apiResponseWriter;
	}

	// 토큰 없는 요청은 X-Mock-Role 헤더의 개발용 회원으로 인증 (토큰이 있으면 토큰 우선)
	@Bean
	public SecurityFilterChain bypassSecurityFilterChain(HttpSecurity http) throws Exception {
		return httpSecurityDefaults.apply(http)
			.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
			.addFilterAfter(new MockAuthenticationFilter(mockUserRegistry, properties.defaultRole(), apiResponseWriter),
				JwtAuthenticationFilter.class)
			.build();
	}

	@Bean
	public CorsAllowedHeadersContributor mockRoleCorsHeader() {
		return () -> List.of(MockAuthenticationFilter.MOCK_ROLE_HEADER);
	}

	// 모든 API의 Swagger 문서에 X-Mock-Role 헤더 입력칸 추가
	@Bean
	public OperationCustomizer mockRoleHeaderCustomizer() {
		StringSchema schema = new StringSchema();
		schema.setEnum(Arrays.stream(MockRole.values()).map(Enum::name).toList());
		return (operation, handlerMethod) -> operation.addParametersItem(new HeaderParameter()
			.name(MockAuthenticationFilter.MOCK_ROLE_HEADER)
			.description("[개발용 인증 우회] 토큰 없이 호출할 때 사용할 개발용 회원 역할 (비우면 기본 역할)")
			.required(false)
			.schema(schema));
	}

}
