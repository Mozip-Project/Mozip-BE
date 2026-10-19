package com.mozip.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class SwaggerConfig {

	private static final String JWT_SCHEME = "JWT";

	@Bean
	public OpenAPI openAPI() {
		return new OpenAPI()
			.info(new Info()
				.title("MOZIP API")
				.description("동아리 · 학회 · 학생회 모집 플랫폼 MOZIP 백엔드 API")
				.version("v1"))
			.addSecurityItem(new SecurityRequirement().addList(JWT_SCHEME))
			.components(new Components()
				.addSecuritySchemes(JWT_SCHEME, new SecurityScheme()
					.type(SecurityScheme.Type.HTTP)
					.scheme("bearer")
					.bearerFormat("JWT")));
	}

}
