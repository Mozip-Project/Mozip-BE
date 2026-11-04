package com.mozip.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class TraceIdFilterTest {

	private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

	private final TraceIdFilter traceIdFilter = new TraceIdFilter();

	@Test
	@DisplayName("요청 헤더가 없으면 UUID를 만들어 MDC와 응답 헤더에 넣고, 요청이 끝나면 MDC를 비운다")
	void createTraceIdWhenHeaderIsMissing() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		AtomicReference<String> traceIdInChain = new AtomicReference<>();

		traceIdFilter.doFilter(request, response, chainCapturingMdc(traceIdInChain));

		String traceId = response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
		assertThat(traceId).matches(UUID_PATTERN);
		assertThat(traceIdInChain.get()).isEqualTo(traceId);
		assertThat(MDC.get(TraceIdFilter.TRACE_ID_KEY)).isNull();
	}

	@Test
	@DisplayName("요청 헤더의 traceId가 올바른 형식이면 그대로 재사용한다")
	void reuseValidTraceIdHeader() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "front-abc_123");
		MockHttpServletResponse response = new MockHttpServletResponse();

		traceIdFilter.doFilter(request, response, new MockFilterChain());

		assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER)).isEqualTo("front-abc_123");
	}

	@ParameterizedTest
	@ValueSource(strings = {"evil\nINFO fake log", "has space", "", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
	@DisplayName("요청 헤더의 traceId 형식이 잘못되면 버리고 새 UUID를 만든다")
	void replaceInvalidTraceIdHeader(String invalidTraceId) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(TraceIdFilter.TRACE_ID_HEADER, invalidTraceId);
		MockHttpServletResponse response = new MockHttpServletResponse();

		traceIdFilter.doFilter(request, response, new MockFilterChain());

		assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER)).matches(UUID_PATTERN);
	}

	@Test
	@DisplayName("/error로 재진입하면 처음 요청에서 정한 traceId를 유지한다")
	void keepTraceIdOnErrorDispatch() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setDispatcherType(DispatcherType.ERROR);
		request.setAttribute(TraceIdFilter.TRACE_ID_KEY, "first-request-id");
		MockHttpServletResponse response = new MockHttpServletResponse();
		AtomicReference<String> traceIdInChain = new AtomicReference<>();

		traceIdFilter.doFilter(request, response, chainCapturingMdc(traceIdInChain));

		assertThat(traceIdInChain.get()).isEqualTo("first-request-id");
		assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER)).isEqualTo("first-request-id");
	}

	private MockFilterChain chainCapturingMdc(AtomicReference<String> traceIdInChain) {
		return new MockFilterChain(new HttpServlet() {
			@Override
			protected void service(HttpServletRequest request, HttpServletResponse response) {
				traceIdInChain.set(MDC.get(TraceIdFilter.TRACE_ID_KEY));
			}
		});
	}

}
