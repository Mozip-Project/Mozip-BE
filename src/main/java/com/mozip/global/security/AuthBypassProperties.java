package com.mozip.global.security;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import com.mozip.global.security.mock.MockRole;

// application.yml의 auth.bypass.* 설정 (개발용 인증 우회)
// 켜면 URL 인증 및 @PreAuthorize 검사 해제 (allowedProfiles의 프로필에서만 가능)
// 토큰 없는 요청은 X-Mock-Role 헤더(없으면 defaultRole)의 개발용 회원으로 인증
@ConfigurationProperties("auth.bypass")
public record AuthBypassProperties(
	@DefaultValue("false") boolean enabled,
	@DefaultValue("local") List<String> allowedProfiles,
	@DefaultValue("APPLICANT") MockRole defaultRole
) {

	// 허용되지 않은 프로필에서 켜져 있으면 예외 발생 (앱 시작 실패)
	// 활성 프로필이 모두 허용 목록에 있어야 함 (prod,local처럼 섞이면 거부)
	public boolean isActive(Environment environment) {
		if (!enabled) {
			return false;
		}
		List<String> profiles = allowedProfiles.stream()
			.filter(StringUtils::hasText)
			.map(String::trim)
			.toList();
		List<String> currentProfiles = currentProfiles(environment);
		if (profiles.isEmpty() || !profiles.containsAll(currentProfiles)) {
			throw new IllegalStateException("auth.bypass.enabled는 " + profiles + " 프로필에서만 켤 수 있습니다. "
				+ "현재 프로필: " + currentProfiles);
		}
		return true;
	}

	// 활성 프로필이 없으면 Spring이 default 프로필로 동작하므로 그것으로 검사
	private static List<String> currentProfiles(Environment environment) {
		String[] activeProfiles = environment.getActiveProfiles();
		return List.of(activeProfiles.length > 0 ? activeProfiles : environment.getDefaultProfiles());
	}

}
