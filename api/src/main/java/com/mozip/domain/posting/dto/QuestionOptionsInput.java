package com.mozip.domain.posting.dto;

import java.util.List;

public record QuestionOptionsInput(
	List<String> choices,
	List<String> columns,
	Integer maxRows,
	Integer maxFiles
) {
}
