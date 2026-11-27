package com.mozip.global.logging;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

// 요청 하나가 끝날 때 접근 로그를 한 줄 남김. 본문, 쿼리 문자열, 쿠키, 헤더 값은 남기지 않음
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AccessLogFilter extends OncePerRequestFilter {

	public static final String USER_ID_ATTRIBUTE = AccessLogFilter.class.getName() + ".USER_ID";
	public static final String GRAPHQL_OPERATION_ATTRIBUTE = AccessLogFilter.class.getName() + ".GRAPHQL_OPERATION";
	public static final String GRAPHQL_FIELDS_ATTRIBUTE = AccessLogFilter.class.getName() + ".GRAPHQL_FIELDS";
	public static final String GRAPHQL_ERROR_CODES_ATTRIBUTE = AccessLogFilter.class.getName()
		+ ".GRAPHQL_ERROR_CODES";

	private static final String STARTED_AT_ATTRIBUTE = AccessLogFilter.class.getName() + ".STARTED_AT";

	// 헬스 체크는 프로브가 몇 초마다 불러 로그를 덮으므로 제외
	private static final List<String> SKIPPED_PATH_PREFIXES = List.of("/actuator/health", "/api/health");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
		throws ServletException, IOException {
		if (request.getAttribute(STARTED_AT_ATTRIBUTE) == null) {
			request.setAttribute(STARTED_AT_ATTRIBUTE, System.nanoTime());
		}
		boolean failed = true;
		try {
			filterChain.doFilter(request, response);
			failed = false;
		} finally {
			// 비동기로 넘어간 요청(GraphQL, SSE)은 응답이 끝나고 다시 들어올 때 남김
			if (!isAsyncStarted(request)) {
				write(request, failed ? HttpStatus.INTERNAL_SERVER_ERROR.value() : response.getStatus());
			}
		}
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		return SKIPPED_PATH_PREFIXES.stream().anyMatch(path::startsWith);
	}

	@Override
	protected boolean shouldNotFilterAsyncDispatch() {
		return false;
	}

	private void write(HttpServletRequest request, int status) {
		long durationMs = TimeUnit.NANOSECONDS.toMillis(
			System.nanoTime() - (long)request.getAttribute(STARTED_AT_ATTRIBUTE));
		Object userId = request.getAttribute(USER_ID_ATTRIBUTE);
		Object operation = request.getAttribute(GRAPHQL_OPERATION_ATTRIBUTE);
		Object fields = request.getAttribute(GRAPHQL_FIELDS_ATTRIBUTE);
		Object errorCodes = request.getAttribute(GRAPHQL_ERROR_CODES_ATTRIBUTE);

		// 사람이 읽는 줄에도 같은 값을 넣음 (local 패턴 로그는 키와 값을 따로 찍지 않음)
		StringBuilder message = new StringBuilder()
			.append(request.getMethod()).append(' ').append(request.getRequestURI())
			.append(' ').append(status).append(' ').append(durationMs).append("ms");
		LoggingEventBuilder event = log.atLevel(status >= 500 ? Level.WARN : Level.INFO)
			.addKeyValue("method", request.getMethod())
			.addKeyValue("path", request.getRequestURI())
			.addKeyValue("status", status)
			.addKeyValue("durationMs", durationMs);
		event = append(event, message, "userId", userId);
		event = append(event, message, "operationName", operation);
		event = append(event, message, "fields", fields);
		event = append(event, message, "errorCodes", errorCodes);

		// 비동기 재진입에서는 TraceIdFilter가 돌지 않아 MDC가 비어 있음
		boolean traceIdMissing = MDC.get(TraceIdFilter.TRACE_ID_KEY) == null
			&& request.getAttribute(TraceIdFilter.TRACE_ID_KEY) instanceof String;
		if (traceIdMissing) {
			MDC.put(TraceIdFilter.TRACE_ID_KEY, (String)request.getAttribute(TraceIdFilter.TRACE_ID_KEY));
		}
		try {
			event.log(message.toString());
		} finally {
			if (traceIdMissing) {
				MDC.remove(TraceIdFilter.TRACE_ID_KEY);
			}
		}
	}

	private LoggingEventBuilder append(LoggingEventBuilder event, StringBuilder message, String key, Object value) {
		if (value == null) {
			return event;
		}
		message.append(' ').append(key).append('=').append(value);
		return event.addKeyValue(key, value);
	}

}
