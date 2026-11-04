package com.mozip.global.config;

import java.util.Arrays;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mozip.global.security.ConditionalOnAuthBypass;
import com.mozip.global.security.cookie.AuthCookieManager;
import com.mozip.global.security.mock.MockAuthenticationFilter;
import com.mozip.global.security.mock.MockRole;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class SwaggerConfig {

	private static final String ACCESS_TOKEN_SCHEME = "accessTokenCookie";

	// 로그인 API를 호출하면 이후 요청에 access_token 쿠키가 자동으로 실림
	@Bean
	public OpenAPI openAPI() {
		return new OpenAPI()
			.info(new Info()
				.title("MOZIP API")
				.description("동아리 · 학회 · 학생회 모집 플랫폼 MOZIP 백엔드 API\n\n"
					+ "인증이 필요한 API는 먼저 `POST /auth/login`을 호출하세요. "
					+ "응답의 `access_token` 쿠키가 이후 요청에 자동으로 실립니다.")
				.version("v1"))
			.addSecurityItem(new SecurityRequirement().addList(ACCESS_TOKEN_SCHEME))
			.components(new Components()
				.addSecuritySchemes(ACCESS_TOKEN_SCHEME, new SecurityScheme()
					.type(SecurityScheme.Type.APIKEY)
					.in(SecurityScheme.In.COOKIE)
					.name(AuthCookieManager.ACCESS_TOKEN_COOKIE)));
	}

	// 인증 우회 중에만 모든 API에 X-Mock-Role 헤더 입력칸 추가
	@Bean
	@ConditionalOnAuthBypass
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
