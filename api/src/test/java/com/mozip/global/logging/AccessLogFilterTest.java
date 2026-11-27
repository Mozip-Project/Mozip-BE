package com.mozip.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.async.StandardServletAsyncWebRequest;
import org.springframework.web.context.request.async.WebAsyncUtils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class AccessLogFilterTest {

	private final AccessLogFilter accessLogFilter = new AccessLogFilter();
	private final Logger logger = (Logger)LoggerFactory.getLogger(AccessLogFilter.class);
	private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

	@BeforeEach
	void attachAppender() {
		appender.start();
		logger.addAppender(appender);
	}

	@AfterEach
	void detachAppender() {
		logger.detachAppender(appender);
	}

	@Test
	@DisplayName("요청이 끝나면 method, path, status, 걸린 시간, userId를 한 줄로 남긴다")
	void writeOneLine() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/organizations/1");
		request.setQueryString("token=secret");
		MockHttpServletResponse response = new MockHttpServletResponse();

		accessLogFilter.doFilter(request, response, chain((req, res) -> {
			req.setAttribute(AccessLogFilter.USER_ID_ATTRIBUTE, 7L);
			res.setStatus(404);
		}));

		assertThat(appender.list).hasSize(1);
		ILoggingEvent event = appender.list.getFirst();
		assertThat(event.getLevel()).isEqualTo(Level.INFO);
		assertThat(event.getFormattedMessage()).matches("GET /organizations/1 404 \\d+ms userId=7");
		assertThat(keyValues(event)).contains("method=GET", "path=/organizations/1", "status=404", "userId=7");
	}

	@Test
	@DisplayName("쿼리 문자열과 쿠키, 헤더 값은 로그에 남기지 않는다")
	void doNotWriteSensitiveValues() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/me");
		request.setQueryString("token=query-secret");
		request.addHeader("Cookie", "accessToken=cookie-secret");
		request.addHeader("Authorization", "Bearer header-secret");

		accessLogFilter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

		assertThat(appender.list.getFirst().getFormattedMessage() + keyValues(appender.list.getFirst()))
			.doesNotContain("secret");
	}

	@Test
	@DisplayName("GraphQL 요청은 operationName, 최상위 필드, 에러 코드를 같이 남긴다")
	void writeGraphqlAttributes() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/graphql");

		accessLogFilter.doFilter(request, new MockHttpServletResponse(), chain((req, res) -> {
			req.setAttribute(AccessLogFilter.GRAPHQL_OPERATION_ATTRIBUTE, "MyPostings");
			req.setAttribute(AccessLogFilter.GRAPHQL_FIELDS_ATTRIBUTE, List.of("myPostings"));
			req.setAttribute(AccessLogFilter.GRAPHQL_ERROR_CODES_ATTRIBUTE, List.of("POSTING_404"));
		}));

		ILoggingEvent event = appender.list.getFirst();
		assertThat(event.getFormattedMessage())
			.matches("POST /graphql 200 \\d+ms operationName=MyPostings fields=\\[myPostings] errorCodes=\\[POSTING_404]");
		assertThat(keyValues(event))
			.contains("operationName=MyPostings", "fields=[myPostings]", "errorCodes=[POSTING_404]");
	}

	@Test
	@DisplayName("5xx 응답은 WARN으로 남긴다")
	void warnOnServerError() throws Exception {
		accessLogFilter.doFilter(new MockHttpServletRequest("GET", "/boom"), new MockHttpServletResponse(),
			chain((req, res) -> res.setStatus(503)));

		assertThat(appender.list.getFirst().getLevel()).isEqualTo(Level.WARN);
	}

	@Test
	@DisplayName("처리 중 예외가 필터까지 올라오면 500으로 남기고 예외는 그대로 던진다")
	void writeWhenChainThrows() {
		MockFilterChain chain = chain((req, res) -> {
			throw new ServletException("boom");
		});

		assertThatThrownBy(() -> accessLogFilter.doFilter(new MockHttpServletRequest("GET", "/boom"),
			new MockHttpServletResponse(), chain)).isInstanceOf(ServletException.class);

		assertThat(appender.list.getFirst().getFormattedMessage()).startsWith("GET /boom 500 ");
	}

	@Test
	@DisplayName("비동기로 넘어간 요청은 바로 남기지 않고, 응답이 끝나 다시 들어올 때 traceId와 함께 한 번만 남긴다")
	void writeOnAsyncDispatch() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/graphql");
		request.setAsyncSupported(true);
		request.setAttribute(TraceIdFilter.TRACE_ID_KEY, "trace-1");
		MockHttpServletResponse response = new MockHttpServletResponse();

		accessLogFilter.doFilter(request, response, chain((req, res) -> {
			StandardServletAsyncWebRequest asyncWebRequest = new StandardServletAsyncWebRequest(req, res);
			WebAsyncUtils.getAsyncManager(req).setAsyncWebRequest(asyncWebRequest);
			asyncWebRequest.startAsync();
		}));

		assertThat(appender.list).isEmpty();

		request.setAsyncStarted(false);
		request.setDispatcherType(DispatcherType.ASYNC);
		accessLogFilter.doFilter(request, response, new MockFilterChain());

		assertThat(appender.list).hasSize(1);
		ILoggingEvent event = appender.list.getFirst();
		assertThat(event.getFormattedMessage()).startsWith("POST /graphql 200 ");
		assertThat(event.getMDCPropertyMap()).containsEntry(TraceIdFilter.TRACE_ID_KEY, "trace-1");
	}

	@Test
	@DisplayName("헬스 체크 요청은 남기지 않는다")
	void skipHealthCheck() throws Exception {
		accessLogFilter.doFilter(new MockHttpServletRequest("GET", "/actuator/health/liveness"),
			new MockHttpServletResponse(), new MockFilterChain());
		accessLogFilter.doFilter(new MockHttpServletRequest("GET", "/api/health"), new MockHttpServletResponse(),
			new MockFilterChain());

		assertThat(appender.list).isEmpty();
	}

	private List<String> keyValues(ILoggingEvent event) {
		return event.getKeyValuePairs().stream().map(pair -> pair.key + "=" + pair.value).toList();
	}

	private MockFilterChain chain(Handler handler) {
		return new MockFilterChain(new HttpServlet() {
			@Override
			protected void service(HttpServletRequest request, HttpServletResponse response)
				throws ServletException {
				handler.handle(request, response);
			}
		});
	}

	@FunctionalInterface
	private interface Handler {
		void handle(HttpServletRequest request, HttpServletResponse response) throws ServletException;
	}

}
