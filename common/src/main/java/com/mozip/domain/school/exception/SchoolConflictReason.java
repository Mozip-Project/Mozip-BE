package com.mozip.domain.school.exception;

// SCHOOL_409의 reason으로 응답해 프론트가 분기
public enum SchoolConflictReason {
	// 이미 APPROVED된 회원이 다시 메일 인증이나 재학증명서 제출을 시도함
	ALREADY_VERIFIED,
	// 재학증명서 심사가 아직 PENDING인 상태에서 다시 제출을 시도함
	PENDING_EXISTS,
	// 로그인 여부와 무관하게, 그 학교 메일이 이미 다른(또는 같은) 회원의 APPROVED 인증에 쓰인 상태에서 인증번호를 다시 요청함
	EMAIL_ALREADY_VERIFIED
}
