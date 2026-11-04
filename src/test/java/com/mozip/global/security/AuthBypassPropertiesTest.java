package com.mozip.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.mozip.global.security.mock.MockRole;

class AuthBypassPropertiesTest {

	@Test
	@DisplayName("우회가 꺼져 있으면 프로필과 관계없이 비활성이다")
	void disabled() {
		AuthBypassProperties properties = new AuthBypassProperties(false, List.of("local"), MockRole.APPLICANT);

		assertThat(properties.isActive(environment("prod"))).isFalse();
	}

	@Test
	@DisplayName("허용된 프로필에서 켜면 활성이다")
	void allowedProfile() {
		AuthBypassProperties properties = new AuthBypassProperties(true, List.of("local", " dev "), MockRole.APPLICANT);

		assertThat(properties.isActive(environment("local"))).isTrue();
		assertThat(properties.isActive(environment("dev"))).isTrue();
	}

	@Test
	@DisplayName("허용되지 않은 프로필에서 켜면 예외를 던져 앱이 시작되지 않는다")
	void notAllowedProfile() {
		AuthBypassProperties properties = new AuthBypassProperties(true, List.of("local", "dev"), MockRole.APPLICANT);

		assertThatThrownBy(() -> properties.isActive(environment("prod")))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("prod");
	}

	@Test
	@DisplayName("허용 프로필 목록이 비어 있으면 어디서도 켤 수 없다")
	void emptyAllowedProfiles() {
		AuthBypassProperties properties = new AuthBypassProperties(true, List.of(""), MockRole.APPLICANT);

		assertThatThrownBy(() -> properties.isActive(environment("local")))
			.isInstanceOf(IllegalStateException.class);
	}

	private static MockEnvironment environment(String profile) {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles(profile);
		return environment;
	}

}
