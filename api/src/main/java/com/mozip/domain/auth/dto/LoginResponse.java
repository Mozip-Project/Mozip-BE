package com.mozip.domain.auth.dto;

import com.mozip.domain.user.entity.Role;
import com.mozip.domain.user.entity.User;

import io.swagger.v3.oas.annotations.media.Schema;

public record LoginResponse(

	@Schema(description = "회원 ID", example = "1")
	Long userId,

	@Schema(description = "이름", example = "김모집")
	String name,

	@Schema(description = "로그인 후 이동할 홈 (APPLICANT: 지원자, OPERATOR: 운영진)", example = "APPLICANT")
	Role lastRole

) {

	public static LoginResponse from(User user) {
		return new LoginResponse(user.getId(), user.getName(), user.getLastRole());
	}

}
