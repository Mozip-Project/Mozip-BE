package com.mozip.domain.organization.exception;

// 운영진 초대/팀 저장 입력의 규칙 위반, COMMON_400의 reason으로 응답
public enum MemberInputReason {
	// 휴대폰 번호(01로 시작하는 10~11자리)나 이메일 형식이 아님
	CONTACT_INVALID,
	// 팀 없는 단체에 팀을 지정하거나 팀 목록을 저장하려 함
	NO_TEAM_ORGANIZATION,
	// 그 단체의 팀이 아닌 id
	TEAM_NOT_FOUND,
	// 팀 이름이 비었거나 50자 초과
	TEAM_NAME_INVALID,
	// 한 요청 안에 같은 팀 이름이나 같은 팀 id가 두 번 나옴
	TEAM_DUPLICATED
}
