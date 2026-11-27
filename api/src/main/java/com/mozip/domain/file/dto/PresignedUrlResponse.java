package com.mozip.domain.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record PresignedUrlResponse(

	@Schema(description = "이 URL로 PUT. 10분 유효", example = "https://s3...")
	String uploadUrl,

	@Schema(description = "업로드 후 GraphQL 뮤테이션에 넘길 키", example = "posting-image/2026/11/uuid.png")
	String fileKey,

	@Schema(description = "업로드 완료 후 미리보기용 GET presigned URL. 업로드 전까지는 열리지 않고, 시간이 지나면 만료되니 오래 저장하지 말 것", example = "https://s3...")
	String previewUrl

) {
}
