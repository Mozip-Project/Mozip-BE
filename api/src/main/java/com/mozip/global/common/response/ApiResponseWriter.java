package com.mozip.global.common.response;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.mozip.global.exception.ErrorCode;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

// 컨트롤러를 거치지 않는 필터 단계에서 ApiResponse 형식의 JSON을 직접 응답할 때 사용
@Component
@RequiredArgsConstructor
public class ApiResponseWriter {

	private final ObjectMapper objectMapper;

	public void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
		writeError(response, errorCode, errorCode.getMessage());
	}

	public void writeError(HttpServletResponse response, ErrorCode errorCode, String message) throws IOException {
		response.setStatus(errorCode.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getOutputStream(), ApiResponse.fail(errorCode.getCode(), message));
	}

}
