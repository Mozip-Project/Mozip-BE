package com.mozip.devtools.authbypass;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class AuthBypassPropertiesTest {

	@Test
	@DisplayName("허용된 프로필이면 통과한다 (공백 포함 값도 정리해서 비교)")
	void allowedProfile() {
		AuthBypassProperties properties = properties("local", " dev ");

		assertThatCode(() -> properties.validateProfiles(environment("local"))).doesNotThrowAnyException();
		assertThatCode(() -> properties.validateProfiles(environment("dev"))).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("허용되지 않은 프로필이면 예외를 던져 앱이 시작되지 않는다")
	void notAllowedProfile() {
		assertThatThrownBy(() -> properties("local", "dev").validateProfiles(environment("stage")))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("stage");
	}

	@Test
	@DisplayName("허용된 프로필과 허용되지 않은 프로필이 섞여 있으면 예외를 던진다")
	void mixedProfiles() {
		AuthBypassProperties properties = properties("local", "dev");

		assertThatThrownBy(() -> properties.validateProfiles(environment("stage", "local")))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("stage");
		assertThatThrownBy(() -> properties.validateProfiles(environment("local", "stage")))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("허용된 프로필끼리만 섞여 있으면 통과한다")
	void multipleAllowedProfiles() {
		assertThatCode(() -> properties("local", "dev").validateProfiles(environment("local", "dev")))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("활성 프로필이 없으면 default 프로필로 보고 허용 목록에 없으면 예외를 던진다")
	void noActiveProfile() {
		assertThatThrownBy(() -> properties("local", "dev").validateProfiles(environment()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("default");
	}

	@Test
	@DisplayName("허용 프로필 목록이 비어 있으면 어디서도 켤 수 없다")
	void emptyAllowedProfiles() {
		assertThatThrownBy(() -> properties("").validateProfiles(environment("local")))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("설정으로 prod를 허용 목록에 넣어도 prod에서는 켤 수 없다")
	void productionNeverAllowed() {
		assertThatThrownBy(() -> properties("local", "prod").validateProfiles(environment("prod")))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("prod");
	}

	private static AuthBypassProperties properties(String... allowedProfiles) {
		return new AuthBypassProperties(true, List.of(allowedProfiles), MockRole.APPLICANT);
	}

	private static MockEnvironment environment(String... profiles) {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles(profiles);
		return environment;
	}

}
