package com.mozip.domain.organization.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// application.yml의 mozip.invite.* 설정. 공용 초대 링크 주소 = baseUrl + token (프론트 배포 주소가 바뀌면 여기만 바꿈)
@ConfigurationProperties("mozip.invite")
public record InviteProperties(
	@DefaultValue("https://mozip.kr/invite/") String baseUrl
) {

	public String linkUrl(String token) {
		return baseUrl + token;
	}

}
