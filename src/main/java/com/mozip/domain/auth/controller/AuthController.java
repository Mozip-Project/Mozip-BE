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
import com.mozip.domain.auth.service.AuthService;
import com.mozip.domain.auth.service.AuthTokens;
import com.mozip.domain.auth.service.LoginResult;
import com.mozip.global.common.response.ApiResponse;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.security.cookie.AuthCookieManager;

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
			// 재발급 실패 시 쿠키 삭제 후 에러 응답
			authCookieManager.expireTokenCookies(response);
			throw e;
		}
		return ApiResponse.ok();
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
