package com.mozip.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mozip.global.config.MethodSecurityConfig;

class ConditionalOnAuthBypassTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(BypassOnlyConfig.class, MethodSecurityConfig.class);

	@ParameterizedTest(name = "auth.bypass.enabled={0}")
	@ValueSource(strings = {"false", "off", "no", "0", "FALSE"})
	@DisplayName("우회가 꺼진 값이면 메서드 권한 검사를 로드하고 우회용 빈은 로드하지 않는다")
	void disabledValues(String value) {
		runner.withPropertyValues("auth.bypass.enabled=" + value)
			.run(context -> {
				assertThat(context).hasSingleBean(MethodSecurityConfig.class);
				assertThat(context).doesNotHaveBean("bypassOnlyBean");
			});
	}

	@ParameterizedTest(name = "auth.bypass.enabled={0}")
	@ValueSource(strings = {"true", "on", "yes", "1", "TRUE"})
	@DisplayName("우회가 켜진 값이면 우회용 빈을 로드하고 메서드 권한 검사는 로드하지 않는다")
	void enabledValues(String value) {
		runner.withPropertyValues("auth.bypass.enabled=" + value)
			.run(context -> {
				assertThat(context).doesNotHaveBean(MethodSecurityConfig.class);
				assertThat(context).hasBean("bypassOnlyBean");
			});
	}

	@Test
	@DisplayName("설정이 없으면 우회가 꺼진 것으로 본다")
	void missing() {
		runner.run(context -> {
			assertThat(context).hasSingleBean(MethodSecurityConfig.class);
			assertThat(context).doesNotHaveBean("bypassOnlyBean");
		});
	}

	@Configuration
	static class BypassOnlyConfig {

		@Bean
		@ConditionalOnAuthBypass
		String bypassOnlyBean() {
			return "bypass";
		}

	}

}
