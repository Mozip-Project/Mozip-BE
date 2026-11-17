package com.mozip.global.config.graphql;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

import graphql.scalars.ExtendedScalars;

// 스키마(schema.graphqls)에 선언한 custom scalar 구현 등록
@Configuration
public class GraphqlScalarConfig {

	@Bean
	public RuntimeWiringConfigurer scalarWiringConfigurer() {
		return wiring -> wiring
			.scalar(LocalDateTimeScalar.INSTANCE)
			.scalar(LocalDateScalar.INSTANCE)
			.scalar(ExtendedScalars.LocalTime)
			.scalar(ExtendedScalars.Json);
	}

}
