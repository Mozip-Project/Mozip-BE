package com.mozip.domain.discord.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

// application.yml의 discord.* 설정 (단체 등록 심사 알림)
@ConfigurationProperties("discord")
public record DiscordProperties(
	@DefaultValue("false") boolean enabled,
	String botToken,
	// Interactions 서명 검증용 Ed25519 공개키 (hex 64자)
	String publicKey,
	String reviewChannelId,
	@DefaultValue List<String> allowedRoleIds,
	@DefaultValue List<String> allowedUserIds,
	@DefaultValue("https://discord.com/api/v10") String apiBaseUrl
) {

	// 켜져 있고 봇 토큰과 채널 id가 있어야 실제로 디스코드에 보냄
	public boolean canSend() {
		return enabled && StringUtils.hasText(botToken) && StringUtils.hasText(reviewChannelId);
	}

	// 역할이나 사용자 id 중 하나라도 허용 목록에 있으면 심사 가능, 목록이 둘 다 비면 아무도 못 함
	public boolean isReviewer(String userId, List<String> roleIds) {
		List<String> users = normalize(allowedUserIds);
		List<String> roles = normalize(allowedRoleIds);
		return (userId != null && users.contains(userId)) || roleIds.stream().anyMatch(roles::contains);
	}

	// 환경변수로 "a, b" 형태로 들어와도 쓸 수 있게 공백과 빈 값을 정리
	private static List<String> normalize(List<String> values) {
		return values.stream()
			.filter(StringUtils::hasText)
			.map(String::trim)
			.toList();
	}

}
