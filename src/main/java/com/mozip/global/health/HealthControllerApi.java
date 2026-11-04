package com.mozip.global.health;

import com.mozip.global.common.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

// Swagger 문서용 어노테이션은 Controller 대신 ControllerApi 인터페이스에 작성
@Tag(name = "Health", description = "서버 상태 확인")
public interface HealthControllerApi {

	@Operation(summary = "헬스 체크", description = "서버가 요청을 처리할 수 있는 상태인지 확인합니다. 로그인 없이 호출할 수 있습니다.")
	@SecurityRequirements
	ApiResponse<HealthResponse> health();

}
