package com.mozip.global.logging;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// 요청마다 traceId를 MDC와 X-Trace-Id 응답 헤더에 넣으며, 요청 헤더에 올바른 형식의 값이 있으면 재사용
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

	public static final String TRACE_ID_HEADER = "X-Trace-Id";
	public static final String TRACE_ID_KEY = "traceId";

	// 로그 인젝션 방지를 위해 허용 문자와 길이를 제한
	private static final Pattern VALID_TRACE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
		throws ServletException, IOException {
		String traceId = resolveTraceId(request);
		request.setAttribute(TRACE_ID_KEY, traceId);
		MDC.put(TRACE_ID_KEY, traceId);
		response.setHeader(TRACE_ID_HEADER, traceId);
		try {
			filterChain.doFilter(request, response);
		} finally {
			MDC.remove(TRACE_ID_KEY);
		}
	}

	// /error로 재진입할 때도 필터를 거쳐 같은 traceId 유지
	@Override
	protected boolean shouldNotFilterErrorDispatch() {
		return false;
	}

	private String resolveTraceId(HttpServletRequest request) {
		if (request.getAttribute(TRACE_ID_KEY) instanceof String traceId) {
			return traceId;
		}
		String header = request.getHeader(TRACE_ID_HEADER);
		if (header != null && VALID_TRACE_ID.matcher(header).matches()) {
			return header;
		}
		return UUID.randomUUID().toString();
	}

}
