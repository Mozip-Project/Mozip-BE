package com.mozip.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SignupResponse(

	@Schema(description = "가입한 회원 ID", example = "1")
	Long userId

) {
}
