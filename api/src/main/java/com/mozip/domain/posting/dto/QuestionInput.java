package com.mozip.domain.posting.dto;

import com.mozip.domain.posting.entity.QuestionType;

public record QuestionInput(
	// null = 공통 문항. 2단계에서 먼저 저장된 Position의 실제 id를 가리킴
	Long positionId,
	QuestionType type,
	String title,
	String description,
	boolean required,
	Integer maxLength,
	QuestionOptionsInput options,
	int sortOrder
) {
}
