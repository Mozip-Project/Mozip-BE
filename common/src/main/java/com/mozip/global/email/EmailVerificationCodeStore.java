package com.mozip.global.email;

import java.util.Optional;

// 이메일로 인증번호를 보내고, 받은 코드가 맞는지 판단하는 데 필요한 상태(요청 쿨다운, 코드, 틀린 횟수, 확인 완료 표시)를
// 다루는 공통 인터페이스. 학교 인증에서 쓰던 로직을 일반화한 것으로, 단체 메일 인증처럼 같은 패턴(메일 발송 → 코드 확인)이
// 필요한 다른 도메인도 이 인터페이스를 구현해 재사용할 수 있다. 발송 상태(worker가 실제로 보냈는지) 추적은 도메인마다
// 다를 수 있어 여기 포함하지 않는다
public interface EmailVerificationCodeStore {

	// 재요청 간격 안이면 true
	boolean isOnCooldown(String email);

	void markRequested(String email);

	// 인증번호를 새로 저장. 이전 틀린 횟수와 confirmed 표시는 같이 초기화해야 함
	void saveCode(String email, String code);

	Optional<String> findCode(String email);

	void deleteCode(String email);

	// 틀린 횟수를 올리고, 너무 많이 틀리면 코드를 바로 지워 다음 확인이 "만료"로 응답되게 함
	void recordWrongAttempt(String email);

	// 코드 확인에 성공한 메일인지 (가입/등록 등 다음 단계로 넘어가기 전 확인용)
	boolean isConfirmed(String email);

	void markConfirmed(String email);

	void deleteConfirmed(String email);

}
