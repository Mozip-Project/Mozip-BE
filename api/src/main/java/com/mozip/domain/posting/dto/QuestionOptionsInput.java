package com.mozip.domain.posting.dto;

import java.util.List;

import jakarta.validation.constraints.Positive;

public record QuestionOptionsInput(
	List<String> choices,
	List<String> columns,
	@Positive(message = "최대 행 수는 1 이상이어야 해요")
	Integer maxRows,
	@Positive(message = "최대 첨부 개수는 1개 이상이어야 해요")
	Integer maxFiles
) {
}
