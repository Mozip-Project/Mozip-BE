package com.mozip.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

	@Schema(description = "휴대폰 번호 (숫자만)", example = "01012345678")
	@NotBlank
	String phone,

	@Schema(description = "비밀번호", example = "mozip1234")
	@NotBlank
	// 가입 시 최대 길이와 맞춰 BCrypt에 긴 입력이 전달되지 않도록 함
	@Size(max = 64, message = "비밀번호는 64자 이하여야 합니다.")
	String password

) {
}
