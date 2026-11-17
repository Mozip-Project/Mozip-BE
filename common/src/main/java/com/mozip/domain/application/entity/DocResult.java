package com.mozip.domain.application.entity;

// PENDING = 평가 미완료, HOLD = 보류 (남아 있으면 결과 확정 불가)
public enum DocResult {

	PENDING,
	HOLD,
	PASS,
	FAIL

}
