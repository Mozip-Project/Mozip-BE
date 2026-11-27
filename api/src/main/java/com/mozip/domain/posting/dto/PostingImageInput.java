package com.mozip.domain.posting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostingImageInput(
	// 파일 업로드 URL 발급(POST /api/files/presigned-url, purpose=POSTING_IMAGE)으로 받은 fileKey
	@NotBlank(message = "이미지 파일 키를 입력해주세요")
	@Size(max = 500, message = "이미지 파일 키는 500자까지 입력할 수 있어요")
	String fileKey,
	int sortOrder,
	boolean isRepresentative
) {
}
