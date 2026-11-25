package com.mozip.domain.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record PresignedUrlResponse(

	@Schema(description = "이 URL로 PUT. 10분 유효", example = "https://s3...")
	String uploadUrl,

	@Schema(description = "업로드 후 GraphQL 뮤테이션에 넘길 키", example = "posting-image/2026/11/uuid.png")
	String fileKey,

	@Schema(description = "업로드 완료 후 접근 URL", example = "https://cdn.mozip.kr/...")
	String fileUrl

) {
}
