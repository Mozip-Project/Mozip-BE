package com.mozip.global.common.response;

import org.slf4j.MDC;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mozip.global.logging.TraceIdFilter;

// 공통 응답 형식이며, traceId는 실패 응답에만 담고 성공 응답은 X-Trace-Id 응답 헤더로 확인
public record ApiResponse<T>(
	boolean success,
	String code,
	String message,
	T data,
	@JsonInclude(JsonInclude.Include.NON_NULL) String traceId
) {

	public static <T> ApiResponse<T> ok(T data) {
		return new ApiResponse<>(true, "OK", "요청이 성공했습니다.", data, null);
	}

	public static ApiResponse<Void> ok() {
		return ok(null);
	}

	public static <T> ApiResponse<T> fail(String code, String message) {
		return new ApiResponse<>(false, code, message, null, MDC.get(TraceIdFilter.TRACE_ID_KEY));
	}

}
