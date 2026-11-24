package com.mozip.domain.evaluation.dto;

import com.mozip.domain.application.entity.DocResult;

// 서류 평가 취합 화면의 탭
public enum DocResultTab {

	// 결과를 고르지 않은(PENDING) 지원자까지 전부
	ALL,
	PASS,
	HOLD,
	FAIL;

	public boolean matches(DocResult docResult) {
		return switch (this) {
			case ALL -> true;
			case PASS -> docResult == DocResult.PASS;
			case HOLD -> docResult == DocResult.HOLD;
			case FAIL -> docResult == DocResult.FAIL;
		};
	}

}
