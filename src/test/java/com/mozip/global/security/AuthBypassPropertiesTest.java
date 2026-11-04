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
	@DisplayName("허용된 프로필과 허용되지 않은 프로필이 섞여 있으면 예외를 던진다")
	void mixedProfiles() {
		AuthBypassProperties properties = new AuthBypassProperties(true, List.of("local", "dev"), MockRole.APPLICANT);

		assertThatThrownBy(() -> properties.isActive(environment("prod", "local")))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("prod");
		assertThatThrownBy(() -> properties.isActive(environment("local", "prod")))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("허용된 프로필끼리만 섞여 있으면 활성이다")
	void multipleAllowedProfiles() {
		AuthBypassProperties properties = new AuthBypassProperties(true, List.of("local", "dev"), MockRole.APPLICANT);

		assertThat(properties.isActive(environment("local", "dev"))).isTrue();
	}

	@Test
	@DisplayName("활성 프로필이 없으면 default 프로필로 보고 허용 목록에 없으면 예외를 던진다")
	void noActiveProfile() {
		AuthBypassProperties properties = new AuthBypassProperties(true, List.of("local", "dev"), MockRole.APPLICANT);

		assertThatThrownBy(() -> properties.isActive(environment()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("default");
	}

	@Test
	@DisplayName("허용 프로필 목록이 비어 있으면 어디서도 켤 수 없다")
	void emptyAllowedProfiles() {
		AuthBypassProperties properties = new AuthBypassProperties(true, List.of(""), MockRole.APPLICANT);

		assertThatThrownBy(() -> properties.isActive(environment("local")))
			.isInstanceOf(IllegalStateException.class);
	}

	private static MockEnvironment environment(String... profiles) {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles(profiles);
		return environment;
	}

}
