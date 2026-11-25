package com.mozip.domain.organization.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// mozip.invite.* 설정, 공용 초대 링크 주소 = baseUrl + token
@ConfigurationProperties("mozip.invite")
public record InviteProperties(
	@DefaultValue("https://mo-zip.com/invite/") String baseUrl
) {

	public String linkUrl(String token) {
		return baseUrl + token;
	}

}
