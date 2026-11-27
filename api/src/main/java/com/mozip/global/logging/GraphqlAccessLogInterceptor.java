package com.mozip.global.logging;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.graphql.ResponseError;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import reactor.core.publisher.Mono;

// GraphQL은 실패해도 HTTP 200이라 접근 로그만으로는 구분이 안 됨. operationName, 최상위 필드, 에러 코드를 요청에 적어 AccessLogFilter가 같이 남기게 함
@Component
public class GraphqlAccessLogInterceptor implements WebGraphQlInterceptor {

	// operationName은 클라이언트가 보낸 값이라 GraphQL 이름 형식일 때만 남김 (로그 인젝션 방지)
	private static final Pattern GRAPHQL_NAME = Pattern.compile("[_A-Za-z][_0-9A-Za-z]{0,99}");

	@Override
	public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
		// 응답은 다른 스레드에서 올 수 있으므로 서블릿 요청을 미리 잡아 둠
		if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
			return chain.next(request);
		}
		HttpServletRequest servletRequest = attributes.getRequest();
		String operationName = request.getOperationName();
		if (operationName != null && GRAPHQL_NAME.matcher(operationName).matches()) {
			servletRequest.setAttribute(AccessLogFilter.GRAPHQL_OPERATION_ATTRIBUTE, operationName);
		}
		return chain.next(request).doOnNext(response -> record(servletRequest, response));
	}

	private void record(HttpServletRequest servletRequest, WebGraphQlResponse response) {
		Set<String> fields = new LinkedHashSet<>();
		if (response.getExecutionResult().getData() instanceof Map<?, ?> data) {
			data.keySet().forEach(key -> addName(fields, key));
		}
		Set<String> errorCodes = new LinkedHashSet<>();
		for (ResponseError error : response.getErrors()) {
			// data가 통째로 null이면 실패한 필드는 에러의 path 첫 칸에서만 알 수 있음
			List<Object> path = error.getParsedPath();
			if (!path.isEmpty()) {
				addName(fields, path.getFirst());
			}
			errorCodes.add(errorCode(error));
		}
		if (!fields.isEmpty()) {
			servletRequest.setAttribute(AccessLogFilter.GRAPHQL_FIELDS_ATTRIBUTE, List.copyOf(fields));
		}
		if (!errorCodes.isEmpty()) {
			servletRequest.setAttribute(AccessLogFilter.GRAPHQL_ERROR_CODES_ATTRIBUTE, List.copyOf(errorCodes));
		}
	}

	private void addName(Set<String> names, Object value) {
		if (value instanceof String name && GRAPHQL_NAME.matcher(name).matches()) {
			names.add(name);
		}
	}

	// 우리 예외는 extensions.code가 있고, 문법이나 검증 오류처럼 graphql-java가 만든 에러는 분류 이름을 씀
	private String errorCode(ResponseError error) {
		if (error.getExtensions().get("code") instanceof String code) {
			return code;
		}
		return String.valueOf(error.getErrorType());
	}

}
