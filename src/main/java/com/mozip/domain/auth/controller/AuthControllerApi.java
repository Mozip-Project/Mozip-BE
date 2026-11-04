package com.mozip.domain.auth.controller;

import com.mozip.domain.auth.dto.LoginRequest;
import com.mozip.domain.auth.dto.LoginResponse;
import com.mozip.domain.auth.dto.SignupRequest;
import com.mozip.domain.auth.dto.SignupResponse;
import com.mozip.global.common.response.ApiResponse;
import com.mozip.global.swagger.ApiErrorCodes;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;

// Swagger 문서용 어노테이션은 Controller 대신 ControllerApi 인터페이스에 작성
@Tag(name = "Auth", description = "회원가입, 로그인, 토큰 재발급, 로그아웃 (토큰은 HttpOnly 쿠키로 주고받음)")
public interface AuthControllerApi {

	@Operation(summary = "회원가입", description = "휴대폰 번호와 비밀번호로 가입합니다. 가입 후 로그인 API를 따로 호출해야 합니다.")
	@SecurityRequirements
	@ApiErrorCodes({"COMMON_400", "USER_001"})
	ApiResponse<SignupResponse> signup(SignupRequest request);

	@Operation(summary = "로그인", description = """
		로그인에 성공하면 `access_token`, `refresh_token` 쿠키를 HttpOnly로 발급합니다.
		토큰은 응답 body에 담지 않으며, 이후 요청에는 브라우저가 쿠키를 자동으로 보냅니다.
		(프론트엔드가 다른 주소라면 요청에 `credentials: 'include'`가 필요합니다.)""")
	@SecurityRequirements
	@ApiErrorCodes({"COMMON_400", "AUTH_001", "AUTH_005", "AUTH_006"})
	ApiResponse<LoginResponse> login(LoginRequest request, HttpServletResponse response);

	@Operation(summary = "토큰 재발급", description = """
		`refresh_token` 쿠키로 새 액세스, 리프레시 토큰 쿠키를 발급합니다. 사용한 리프레시 토큰은 폐기됩니다.
		API가 `AUTH_002`(액세스 토큰 만료)로 응답하면 이 API를 호출한 뒤 원래 요청을 다시 보내세요.
		재발급 호출은 한 번에 하나만 보내세요. 같은 쿠키로 동시에 보내면 늦게 처리된 쪽은 `AUTH_007`을 받는데,
		이때 쿠키는 그대로이므로 원래 요청을 다시 보내면 됩니다.
		`AUTH_004`, `AUTH_005`면 쿠키를 지우므로 로그인 화면으로 이동하면 됩니다.
		`AUTH_006`은 잠시 후 재시도하고, 재시도가 `AUTH_004`로 끝나면 로그인 화면으로 이동하면 됩니다.""")
	@SecurityRequirements
	@ApiErrorCodes({"AUTH_004", "AUTH_005", "AUTH_006", "AUTH_007"})
	ApiResponse<Void> reissue(@Parameter(hidden = true) String refreshToken, HttpServletResponse response);

	@Operation(summary = "로그아웃", description = """
		리프레시 토큰을 폐기하고 토큰 쿠키를 삭제합니다. 토큰이 없거나 만료돼도 성공으로 응답합니다.
		`AUTH_006`이면 토큰을 폐기하지 못한 것이므로 쿠키를 유지합니다. 잠시 후 다시 호출해 주세요.""")
	@SecurityRequirements
	@ApiErrorCodes({"AUTH_006"})
	ApiResponse<Void> logout(@Parameter(hidden = true) String refreshToken, HttpServletResponse response);

}
