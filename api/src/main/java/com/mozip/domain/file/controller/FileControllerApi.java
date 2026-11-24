package com.mozip.domain.file.controller;

import com.mozip.domain.file.dto.PresignedUrlRequest;
import com.mozip.domain.file.dto.PresignedUrlResponse;
import com.mozip.global.common.response.ApiResponse;
import com.mozip.global.swagger.ApiErrorCodes;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

// Swagger 문서용 어노테이션은 Controller 대신 ControllerApi 인터페이스에 작성
@Tag(name = "File", description = "S3 presigned URL 발급 (업로드는 프론트가 S3로 직접 PUT)")
public interface FileControllerApi {

	@Operation(summary = "파일 업로드 URL 발급", description = """
		S3 Presigned URL을 발급합니다. 응답의 uploadUrl로 프론트가 파일을 직접 PUT하고,
		fileKey를 이후 GraphQL 뮤테이션(로고, 공고 이미지, 재학증명서, 지원서 첨부)에 전달합니다.
		용량, 허용 contentType 제한은 purpose별로 다릅니다.""")
	@ApiErrorCodes({"COMMON_400", "FILE_413", "FILE_415"})
	ApiResponse<PresignedUrlResponse> issuePresignedUrl(PresignedUrlRequest request);

}
