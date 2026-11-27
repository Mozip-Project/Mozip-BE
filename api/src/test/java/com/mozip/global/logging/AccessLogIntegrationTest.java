package com.mozip.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.mozip.TestcontainersConfiguration;
import com.mozip.devtools.authbypass.MockRole;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.security.AdminOnlyTestController;

// 컨텍스트를 같이 쓰도록 AuthBypassIntegrationTest와 설정을 맞춤
@Import({TestcontainersConfiguration.class, AdminOnlyTestController.class})
@SpringBootTest(properties = {"auth.bypass.enabled=true", "auth.cors.allowed-origins=https://allowed.test"})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AccessLogIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Test
	@DisplayName("REST 요청의 접근 로그에 인증된 회원의 userId와 traceId가 남는다")
	void restAccessLog(CapturedOutput output) throws Exception {
		Long applicantId = userRepository.findByPhone(MockRole.APPLICANT.getPhone()).orElseThrow().getId();

		mockMvc.perform(get(AdminOnlyTestController.ME_PATH).header(TraceIdFilter.TRACE_ID_HEADER, "rest-trace-1"))
			.andExpect(status().isOk());

		assertThat(accessLogLine(output, "rest-trace-1"))
			.contains("GET /test/me 200 ")
			.contains("userId=" + applicantId);
	}

	@Test
	@DisplayName("GraphQL 요청의 접근 로그에 operationName과 최상위 필드가 남는다")
	void graphqlAccessLog(CapturedOutput output) throws Exception {
		graphql("graphql-trace-1", """
			{"operationName": "Health", "query": "query Health { health { status } }"}
			""");

		assertThat(accessLogLine(output, "graphql-trace-1"))
			.contains("POST /graphql 200 ")
			.contains("userId=")
			.contains("operationName=Health")
			.contains("fields=[health]")
			.doesNotContain("errorCodes");
	}

	@Test
	@DisplayName("GraphQL이 HTTP 200에 errors로 응답해도 접근 로그에 에러 코드가 남는다")
	void graphqlErrorCode(CapturedOutput output) throws Exception {
		graphql("graphql-trace-2", """
			{"query": "{ noSuchField }"}
			""");

		assertThat(accessLogLine(output, "graphql-trace-2"))
			.contains("POST /graphql 200 ")
			.contains("errorCodes=[ValidationError]");
	}

	@Test
	@DisplayName("요청 본문은 접근 로그에 남기지 않는다")
	void doNotWriteBody(CapturedOutput output) throws Exception {
		graphql("graphql-trace-3", """
			{"query": "{ health { status } }", "variables": {"memo": "body-secret"}}
			""");

		assertThat(accessLogLine(output, "graphql-trace-3"))
			.contains("fields=[health]")
			.doesNotContain("body-secret")
			.doesNotContain("variables");
	}

	private void graphql(String traceId, String body) throws Exception {
		MvcResult result = mockMvc.perform(post("/graphql")
				.header(TraceIdFilter.TRACE_ID_HEADER, traceId)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.content(body))
			.andReturn();
		// 응답이 바로 준비되면 비동기로 넘어가지 않음
		if (result.getRequest().isAsyncStarted()) {
			mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());
		}
	}

	private String accessLogLine(CapturedOutput output, String traceId) {
		return output.getOut().lines()
			.filter(line -> line.contains(AccessLogFilter.class.getSimpleName()) && line.contains(traceId))
			.findFirst()
			.orElseThrow(() -> new AssertionError("접근 로그가 없습니다: " + traceId));
	}

}
