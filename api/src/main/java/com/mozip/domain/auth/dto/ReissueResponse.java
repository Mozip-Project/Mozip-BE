package com.mozip.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ReissueResponse(

	@Schema(description = "새 access_token 쿠키가 만료되기까지 남은 시간(초). 로그인 응답과 같은 값으로 다음 재발급 타이머를 다시 잡음", example = "1800")
	long accessTokenExpiresIn

) {
}
