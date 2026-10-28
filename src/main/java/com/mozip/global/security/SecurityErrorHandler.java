package com.mozip.global.security;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.mozip.global.common.response.ApiResponse;
import com.mozip.global.exception.CommonErrorCode;
import com.mozip.global.exception.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

// Security 필터 단계의 401/403은 GlobalExceptionHandler를 거치지 않아 여기서 ApiResponse 형식으로 직접 응답
@Component
@RequiredArgsConstructor
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final ObjectMapper objectMapper;

	// 인증 정보가 없거나 유효하지 않을 때 (401)
	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
		AuthenticationException authException) throws IOException {
		writeErrorResponse(response, CommonErrorCode.UNAUTHORIZED);
	}

	// 인증은 됐지만 권한이 없을 때 (403)
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
		AccessDeniedException accessDeniedException) throws IOException {
		writeErrorResponse(response, CommonErrorCode.FORBIDDEN);
	}

	private void writeErrorResponse(HttpServletResponse response, ErrorCode errorCode) throws IOException {
		response.setStatus(errorCode.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getOutputStream(), ApiResponse.fail(errorCode.getCode(), errorCode.getMessage()));
	}

}
