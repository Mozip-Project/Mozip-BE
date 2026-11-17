package com.mozip.global.security;

import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

// 인증된 사용자 정보 (@AuthenticationPrincipal AuthUser로 받음)
public record AuthUser(Long userId, Authority authority) {

	public List<GrantedAuthority> grantedAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + authority.name()));
	}

	// SecurityContext에 넣을 인증 객체 (비밀번호 검증은 이미 끝났으므로 credentials 없음)
	public Authentication toAuthentication() {
		return UsernamePasswordAuthenticationToken.authenticated(this, null, grantedAuthorities());
	}

}
