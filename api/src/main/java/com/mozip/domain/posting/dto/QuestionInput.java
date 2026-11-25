package com.mozip.domain.posting.dto;

import com.mozip.domain.posting.entity.QuestionType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record QuestionInput(
	// null = 공통 문항. 2단계에서 먼저 저장된 Position의 실제 id를 가리킴
	Long positionId,
	QuestionType type,
	@NotBlank(message = "문항 제목을 입력해주세요")
	@Size(max = 200, message = "문항 제목은 200자까지 입력할 수 있어요")
	String title,
	@Size(max = 500, message = "문항 설명은 500자까지 입력할 수 있어요")
	String description,
	boolean required,
	@Positive(message = "최대 글자 수는 1자 이상이어야 해요")
	Integer maxLength,
	@Valid QuestionOptionsInput options,
	int sortOrder
) {
}
