package com.mozip.devtools.authbypass;

import com.mozip.domain.user.entity.Role;
import com.mozip.global.security.Authority;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// X-Mock-Role 헤더로 고르는 개발용 회원 (비밀번호는 무작위라 /auth/login 불가)
// OPERATOR는 단체 소속(members) 없음, 단체 기능 구현 시 추가 필요
@Getter
@RequiredArgsConstructor
public enum MockRole {

	APPLICANT("01000000001", "개발용 지원자", Authority.USER, Role.APPLICANT),
	OPERATOR("01000000002", "개발용 운영진", Authority.USER, Role.OPERATOR),
	ADMIN("01000000003", "개발용 관리자", Authority.ADMIN, Role.APPLICANT);

	private final String phone;
	private final String userName;
	private final Authority authority;
	private final Role lastRole;

}
