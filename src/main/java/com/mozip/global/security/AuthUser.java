package com.mozip.global.security;

import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.mozip.domain.user.entity.Authority;

// 인증된 사용자 정보 (@AuthenticationPrincipal AuthUser로 받음)
public record AuthUser(Long userId, Authority authority) {

	public List<GrantedAuthority> grantedAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + authority.name()));
	}

}
