package com.mozip.domain.school.exception;

// verifySchoolEmail/가입 입력 검증 실패 (SCHOOL_400의 reason으로 응답해 프론트가 분기)
public enum SchoolInputReason {
	// 인증번호가 Redis에 저장된 값과 다름
	CODE_MISMATCH,
	// 5분이 지나 Redis에서 이미 사라졌거나, 틀린 횟수가 많아 서버가 먼저 지움
	CODE_EXPIRED,
	// 가입 시점에 이 메일이 verifySchoolEmail로 확인된 적이 없거나 확인 후 10분이 지나 다시 확인해야 함
	EMAIL_NOT_VERIFIED
}
