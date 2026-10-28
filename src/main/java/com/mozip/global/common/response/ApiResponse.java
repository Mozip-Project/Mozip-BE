package com.mozip.global.common.response;

/**
 * 공통 응답 형식
 * 성공: { "success": true, "code": "OK", "message": "요청이 성공했습니다.", "data": { ... } }
 * 실패: { "success": false, "code": "COMMON_400", "message": "잘못된 요청입니다.", "data": null }
 */
public record ApiResponse<T>(boolean success, String code, String message, T data) {

	public static <T> ApiResponse<T> ok(T data) {
		return new ApiResponse<>(true, "OK", "요청이 성공했습니다.", data);
	}

	public static ApiResponse<Void> ok() {
		return ok(null);
	}

	public static <T> ApiResponse<T> fail(String code, String message) {
		return new ApiResponse<>(false, code, message, null);
	}

}
