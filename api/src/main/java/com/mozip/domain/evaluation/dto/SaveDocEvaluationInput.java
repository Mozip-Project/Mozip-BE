package com.mozip.domain.evaluation.dto;

import java.util.List;

import com.mozip.domain.evaluation.entity.Opinion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

// 자동 저장은 비어 있어도 되므로 필수는 applicationId뿐. 의견, 점수, 메모 길이는 서비스에서 reason과 함께 검사한다
public record SaveDocEvaluationInput(
	@NotNull(message = "지원서를 선택해주세요.") Long applicationId,
	Opinion opinion,
	List<@Valid DocScoreInput> scores,
	String memo
) {
}
