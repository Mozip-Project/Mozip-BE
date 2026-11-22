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
	Role lastRole,

	@Schema(description = "access_token 쿠키가 만료되기까지 남은 시간(초). 쿠키가 HttpOnly라 프론트가 만료를 알 수 없어 응답에 같이 줌", example = "1800")
	long accessTokenExpiresIn

) {

	public static LoginResponse from(User user, long accessTokenExpiresIn) {
		return new LoginResponse(user.getId(), user.getName(), user.getLastRole(), accessTokenExpiresIn);
	}

}
