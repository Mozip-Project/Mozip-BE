package com.mozip.domain.application.dto;

import com.mozip.domain.application.entity.AttachmentType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AttachmentInput(
	@NotNull(message = "첨부 유형을 선택해주세요")
	AttachmentType type,
	// FILE = presigned URL(purpose=APPLICATION_ATTACHMENT)로 올린 파일의 키/URL, LINK = 지원자가 입력한 링크
	@NotBlank(message = "주소를 입력해주세요")
	@Size(max = 500, message = "최대 500자까지 입력할 수 있어요")
	String url,
	@Size(max = 255, message = "최대 255자까지 입력할 수 있어요")
	String fileName
) {
}
