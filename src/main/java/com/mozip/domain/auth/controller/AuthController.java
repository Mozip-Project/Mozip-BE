package com.mozip.domain.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mozip.domain.auth.dto.LoginRequest;
import com.mozip.domain.auth.dto.LoginResponse;
import com.mozip.domain.auth.dto.SignupRequest;
import com.mozip.domain.auth.dto.SignupResponse;
import com.mozip.domain.auth.exception.RefreshTokenConsumedException;
import com.mozip.domain.auth.service.AuthService;
import com.mozip.domain.auth.service.AuthTokens;
import com.mozip.domain.auth.service.LoginResult;
import com.mozip.global.common.response.ApiResponse;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.ErrorCode;
import com.mozip.global.security.cookie.AuthCookieManager;
import com.mozip.global.security.jwt.TokenErrorCode;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController implements AuthControllerApi {

	private final AuthService authService;
	private final AuthCookieManager authCookieManager;

	@Override
	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<SignupResponse> signup(@RequestBody @Valid SignupRequest request) {
		return ApiResponse.ok(authService.signup(request));
	}

	@Override
	@PostMapping("/login")
	public ApiResponse<LoginResponse> login(@RequestBody @Valid LoginRequest request,
		HttpServletResponse response) {
		LoginResult result = authService.login(request);
		addTokenCookies(response, result.tokens());
		return ApiResponse.ok(result.user());
	}

	@Override
	@PostMapping("/reissue")
	public ApiResponse<Void> reissue(
		@CookieValue(name = AuthCookieManager.REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
		HttpServletResponse response) {
		try {
			addTokenCookies(response, authService.reissue(refreshToken));
		} catch (BusinessException e) {
			if (shouldExpireCookies(e)) {
				authCookieManager.expireTokenCookies(response);
			}
			throw e;
		}
		return ApiResponse.ok();
	}

	// 재발급 실패 시 기본은 쿠키 삭제이고, 같은 쿠키가 아직 쓸모 있는 두 경우만 유지
	// AUTH_007: 먼저 처리된 요청이 발급한 새 쿠키를 이 응답이 지우면 안 됨
	// 토큰을 소비하기 전의 AUTH_006: Redis가 복구되면 같은 쿠키로 재시도 가능
	private static boolean shouldExpireCookies(BusinessException e) {
		ErrorCode errorCode = e.getErrorCode();
		if (errorCode == TokenErrorCode.DUPLICATE_REISSUE) {
			return false;
		}
		if (errorCode == TokenErrorCode.TOKEN_STORE_UNAVAILABLE) {
			return e instanceof RefreshTokenConsumedException;
		}
		return true;
	}

	@Override
	@PostMapping("/logout")
	public ApiResponse<Void> logout(
		@CookieValue(name = AuthCookieManager.REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
		HttpServletResponse response) {
		authService.logout(refreshToken);
		authCookieManager.expireTokenCookies(response);
		return ApiResponse.ok();
	}

	private void addTokenCookies(HttpServletResponse response, AuthTokens tokens) {
		authCookieManager.addTokenCookies(response, tokens.accessToken(), tokens.refreshToken());
	}

}
