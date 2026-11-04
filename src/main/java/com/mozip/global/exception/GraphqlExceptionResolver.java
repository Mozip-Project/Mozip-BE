package com.mozip.global.exception;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import com.mozip.global.common.response.FieldErrorResponse;
import com.mozip.global.logging.TraceIdFilter;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

// GraphQL 예외를 errors[]로 변환하며, 프론트가 REST와 같은 기준으로 처리하도록 extensions에 code와 traceId를 담음
// Spring Security가 등록하는 기본 리졸버보다 먼저 실행해 권한 거부도 같은 형식으로 응답
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GraphqlExceptionResolver extends DataFetcherExceptionResolverAdapter {

	@Override
	protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
		if (ex instanceof BusinessException e) {
			ErrorCode errorCode = e.getErrorCode();
			// 4xx는 warn, 5xx는 장애 발생이므로 error
			if (errorCode.getStatus().is5xxServerError()) {
				log.error("Business exception: code={}, context={}", errorCode.getCode(), e.getContext(), e);
			} else {
				log.warn("Business exception: code={}, context={}", errorCode.getCode(), e.getContext());
			}
			return toGraphqlError(errorCode, e.getMessage(), env, null);
		}
		// @PreAuthorize 거부 (인증 자체는 GraphqlAuthInstrumentation이 먼저 검사하므로 여기 오면 권한 부족)
		if (ex instanceof AccessDeniedException) {
			log.warn("Access denied: {}", ex.getMessage());
			return toGraphqlError(CommonErrorCode.FORBIDDEN, CommonErrorCode.FORBIDDEN.getMessage(), env, null);
		}
		if (ex instanceof ConstraintViolationException e) {
			List<FieldErrorResponse> fieldErrors = e.getConstraintViolations().stream()
				.map(FieldErrorResponse::from)
				.toList();
			String message = fieldErrors.stream()
				.findFirst()
				.map(FieldErrorResponse::toMessage)
				.orElse(CommonErrorCode.INVALID_INPUT.getMessage());
			return toGraphqlError(CommonErrorCode.INVALID_INPUT, message, env, fieldErrors);
		}
		log.error("Unhandled exception", ex);
		return toGraphqlError(CommonErrorCode.INTERNAL_SERVER_ERROR, CommonErrorCode.INTERNAL_SERVER_ERROR.getMessage(),
			env, null);
	}

	private GraphQLError toGraphqlError(ErrorCode errorCode, String message, DataFetchingEnvironment env,
		List<FieldErrorResponse> fieldErrors) {
		Map<String, Object> extensions = new LinkedHashMap<>();
		extensions.put("code", errorCode.getCode());
		String traceId = MDC.get(TraceIdFilter.TRACE_ID_KEY);
		if (traceId != null) {
			extensions.put("traceId", traceId);
		}
		// REST 실패 응답의 data와 같은 필드별 에러 목록
		if (fieldErrors != null) {
			extensions.put("fieldErrors", fieldErrors);
		}
		return GraphqlErrorBuilder.newError(env)
			.errorType(toErrorType(errorCode))
			.message(message)
			.extensions(extensions)
			.build();
	}

	private ErrorType toErrorType(ErrorCode errorCode) {
		return switch (errorCode.getStatus()) {
			case BAD_REQUEST -> ErrorType.BAD_REQUEST;
			case UNAUTHORIZED -> ErrorType.UNAUTHORIZED;
			case FORBIDDEN -> ErrorType.FORBIDDEN;
			case NOT_FOUND -> ErrorType.NOT_FOUND;
			default -> ErrorType.INTERNAL_ERROR;
		};
	}

}
