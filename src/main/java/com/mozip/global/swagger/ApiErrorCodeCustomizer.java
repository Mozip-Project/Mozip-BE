package com.mozip.global.swagger;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.mozip.global.exception.CommonErrorCode;
import com.mozip.global.exception.ErrorCode;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import lombok.RequiredArgsConstructor;

// @ApiErrorCodes의 에러 코드를 상태 코드별 응답 예시로 추가하고, 500 예시는 모든 API에 자동 추가
@Component
@RequiredArgsConstructor
public class ApiErrorCodeCustomizer implements OperationCustomizer {

	private static final String EXAMPLE_TRACE_ID = "3f2b1c9e-8d4a-4f6b-9c7e-1a2b3c4d5e6f";

	private final ErrorCodeRegistry errorCodeRegistry;

	@Override
	public Operation customize(Operation operation, HandlerMethod handlerMethod) {
		List<ErrorCode> errorCodes = new ArrayList<>();
		ApiErrorCodes apiErrorCodes = handlerMethod.getMethodAnnotation(ApiErrorCodes.class);
		if (apiErrorCodes != null) {
			for (String code : apiErrorCodes.value()) {
				errorCodes.add(errorCodeRegistry.get(code));
			}
		}
		errorCodes.add(CommonErrorCode.INTERNAL_SERVER_ERROR);

		Map<HttpStatus, List<ErrorCode>> errorCodesByStatus = errorCodes.stream()
			.distinct()
			.collect(groupingBy(ErrorCode::getStatus, () -> new TreeMap<>(Comparator.comparingInt(HttpStatus::value)),
				toList()));
		errorCodesByStatus.forEach((status, codes) ->
			operation.getResponses().addApiResponse(String.valueOf(status.value()), toApiResponse(codes)));
		return operation;
	}

	// 문서를 처음 요청할 때가 아니라 앱 시작 시점에 잘못된 코드를 잡기 위해 미리 검증
	@EventListener(ContextRefreshedEvent.class)
	public void validateApiErrorCodes(ContextRefreshedEvent event) {
		RequestMappingHandlerMapping handlerMapping = event.getApplicationContext()
			.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
		handlerMapping.getHandlerMethods().values().stream()
			.map(handlerMethod -> handlerMethod.getMethodAnnotation(ApiErrorCodes.class))
			.filter(apiErrorCodes -> apiErrorCodes != null)
			.flatMap(apiErrorCodes -> List.of(apiErrorCodes.value()).stream())
			.forEach(errorCodeRegistry::get);
	}

	private ApiResponse toApiResponse(List<ErrorCode> errorCodes) {
		MediaType mediaType = new MediaType();
		errorCodes.forEach(errorCode -> mediaType.addExamples(errorCode.getCode(), new Example()
			.summary(errorCode.getMessage())
			.value(toExampleBody(errorCode))));

		String description = errorCodes.stream()
			.map(errorCode -> "- `" + errorCode.getCode() + "` " + errorCode.getMessage())
			.collect(joining("\n"));
		return new ApiResponse()
			.description(description)
			.content(new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE, mediaType));
	}

	// 실제 실패 응답(ApiResponse.fail)과 같은 형태의 예시 본문
	private Map<String, Object> toExampleBody(ErrorCode errorCode) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("success", false);
		body.put("code", errorCode.getCode());
		body.put("message", errorCode.getMessage());
		body.put("data", null);
		body.put("traceId", EXAMPLE_TRACE_ID);
		return body;
	}

}
