package com.mozip.domain.posting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostingImageInput(
	@NotBlank(message = "이미지 주소를 입력해주세요")
	@Size(max = 500, message = "이미지 주소는 500자까지 입력할 수 있어요")
	String url,
	int sortOrder,
	boolean isRepresentative
) {
}
