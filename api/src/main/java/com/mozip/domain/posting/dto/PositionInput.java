package com.mozip.domain.posting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PositionInput(
	Long teamId,
	@NotBlank(message = "포지션 이름을 입력해주세요")
	@Size(max = 50, message = "포지션 이름은 50자까지 입력할 수 있어요")
	String name,
	@Positive(message = "모집 인원은 1명 이상이어야 해요")
	Integer headcount,
	int sortOrder
) {
}
