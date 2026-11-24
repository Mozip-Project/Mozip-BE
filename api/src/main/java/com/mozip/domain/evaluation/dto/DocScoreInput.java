package com.mozip.domain.evaluation.dto;

import jakarta.validation.constraints.NotNull;

// 점수 범위(1~5)는 reason으로 알려야 해서 어노테이션 대신 서비스에서 검사한다
public record DocScoreInput(
	@NotNull(message = "문항을 선택해주세요.") Long questionId,
	@NotNull(message = "점수를 선택해주세요.") Integer score
) {
}
