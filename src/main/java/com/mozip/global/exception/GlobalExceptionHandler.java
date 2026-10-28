package com.mozip.global.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.mozip.global.common.response.ApiResponse;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

// Spring MVC 표준 예외는 상위 클래스가 상태 코드를 정하고, 여기서는 body만 ApiResponse 형식으로 바꿈
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
		ErrorCode errorCode = e.getErrorCode();
		// 4xx는 warn, 5xx는 장애 발생이므로 error
		if (errorCode.getStatus().is5xxServerError()) {
			log.error("Business exception: code={}, context={}", errorCode.getCode(), e.getContext(), e);
		} else {
			log.warn("Business exception: code={}, context={}", errorCode.getCode(), e.getContext());
		}
		return toResponse(errorCode, e.getMessage());
	}

	// 서비스 계층의 @Validated 검증 실패
	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(ConstraintViolationException e) {
		String message = e.getConstraintViolations().stream()
			.findFirst()
			.map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
			.orElse(CommonErrorCode.INVALID_INPUT.getMessage());
		return toResponse(CommonErrorCode.INVALID_INPUT, message);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
		log.error("Unhandled exception", e);
		return toResponse(CommonErrorCode.INTERNAL_SERVER_ERROR, CommonErrorCode.INTERNAL_SERVER_ERROR.getMessage());
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
		HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		String message = ex.getBindingResult().getFieldErrors().stream()
			.findFirst()
			.map(error -> error.getField() + ": " + error.getDefaultMessage())
			.orElse(CommonErrorCode.INVALID_INPUT.getMessage());
		return ResponseEntity.status(status).headers(headers)
			.body(ApiResponse.fail(CommonErrorCode.INVALID_INPUT.getCode(), message));
	}

	// HTTP 상태는 Spring이 정한 값(405의 Allow 헤더 포함)을 그대로 쓰고, code/message만 공통 코드로 맞춤
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
		HttpStatusCode statusCode, WebRequest request) {
		// 응답이 이미 나간 뒤에는 body를 쓸 수 없으므로 상위 클래스와 같이 처리하지 않음
		if (request instanceof ServletWebRequest servletWebRequest) {
			HttpServletResponse response = servletWebRequest.getResponse();
			if (response != null && response.isCommitted()) {
				return null;
			}
		}
		if (statusCode.is5xxServerError()) {
			log.error("Spring MVC exception", ex);
		}
		ErrorCode errorCode = toErrorCode(statusCode);
		return ResponseEntity.status(statusCode).headers(headers)
			.body(ApiResponse.fail(errorCode.getCode(), errorCode.getMessage()));
	}

	// 맞는 공통 코드가 없는 상태는 4xx면 INVALID_INPUT, 5xx면 INTERNAL_SERVER_ERROR로 응답
	private ErrorCode toErrorCode(HttpStatusCode statusCode) {
		return switch (statusCode.value()) {
			case 400 -> CommonErrorCode.INVALID_INPUT;
			case 404 -> CommonErrorCode.NOT_FOUND;
			case 405 -> CommonErrorCode.METHOD_NOT_ALLOWED;
			case 413 -> CommonErrorCode.CONTENT_TOO_LARGE;
			case 415 -> CommonErrorCode.UNSUPPORTED_MEDIA_TYPE;
			default -> statusCode.is4xxClientError() ? CommonErrorCode.INVALID_INPUT : CommonErrorCode.INTERNAL_SERVER_ERROR;
		};
	}

	private ResponseEntity<ApiResponse<Void>> toResponse(ErrorCode errorCode, String message) {
		return ResponseEntity.status(errorCode.getStatus())
			.body(ApiResponse.fail(errorCode.getCode(), message));
	}

}
