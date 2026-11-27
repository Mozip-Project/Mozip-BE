package com.mozip.domain.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PresignedUrlRequest(

	@Schema(description = "업로드 용도. 허용 확장자와 용량 제한이 달라짐", example = "POSTING_IMAGE")
	@NotNull
	FilePurpose purpose,

	@Schema(description = "원본 파일명", example = "poster.png")
	@NotBlank
	@Size(max = 255)
	String fileName,

	@Schema(description = "MIME 타입", example = "image/png")
	@NotBlank
	String contentType,

	@Schema(description = "바이트 크기", example = "482113")
	@NotNull
	@Positive
	Long size

) {
}
