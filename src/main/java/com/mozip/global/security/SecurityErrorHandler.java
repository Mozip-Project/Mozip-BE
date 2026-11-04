package com.mozip.global.security;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.mozip.global.common.response.ApiResponseWriter;
import com.mozip.global.exception.CommonErrorCode;
import com.mozip.global.exception.ErrorCode;
import com.mozip.global.security.jwt.JwtAuthenticationFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

// Security 필터 단계의 401/403은 GlobalExceptionHandler를 거치지 않아 여기서 ApiResponse 형식으로 직접 응답
@Component
@RequiredArgsConstructor
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final ApiResponseWriter apiResponseWriter;

	// 인증 정보가 없거나 유효하지 않을 때 (401)
	// 토큰 만료 AUTH_002, 위조 AUTH_003, 토큰 없음 COMMON_401
	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
		AuthenticationException authException) throws IOException {
		ErrorCode errorCode = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE) instanceof ErrorCode
			authError ? authError : CommonErrorCode.UNAUTHORIZED;
		apiResponseWriter.writeError(response, errorCode);
	}

	// 인증은 됐지만 권한이 없을 때 (403)
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
		AccessDeniedException accessDeniedException) throws IOException {
		apiResponseWriter.writeError(response, CommonErrorCode.FORBIDDEN);
	}

}
