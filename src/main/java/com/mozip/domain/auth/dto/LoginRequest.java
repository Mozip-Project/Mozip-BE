package com.mozip.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

	@Schema(description = "휴대폰 번호 (숫자만)", example = "01012345678")
	@NotBlank
	String phone,

	@Schema(description = "비밀번호", example = "mozip1234")
	@NotBlank
	String password

) {
}
