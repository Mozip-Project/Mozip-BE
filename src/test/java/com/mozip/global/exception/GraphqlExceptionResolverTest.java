package com.mozip.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.stereotype.Controller;

import com.mozip.TestcontainersConfiguration;
import com.mozip.global.logging.TraceIdFilter;

import jakarta.validation.constraints.NotBlank;

@Import({TestcontainersConfiguration.class, GraphqlExceptionResolverTest.TestController.class})
@SpringBootTest(properties = "spring.graphql.schema.locations=classpath:graphql/,classpath:graphql-test/")
@AutoConfigureMockMvc
@AutoConfigureHttpGraphQlTester
@ExtendWith(OutputCaptureExtension.class)
class GraphqlExceptionResolverTest {

	private static final String TRACE_ID = "front-abc-1";

	@Autowired
	private HttpGraphQlTester graphQlTester;

	@Test
	@DisplayName("4xx BusinessException은 errors[]의 extensions에 code와 traceId를 담고, WARN 로그를 남긴다")
	void resolveClientBusinessException(CapturedOutput output) {
		withTraceId().document("{ testNotFound }")
			.execute()
			.errors()
			.satisfy(errors -> {
				assertThat(errors).hasSize(1);
				assertThat(errors.getFirst().getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
				assertThat(errors.getFirst().getMessage()).isEqualTo("요청한 리소스를 찾을 수 없습니다.");
				assertThat(errors.getFirst().getPath()).isEqualTo("testNotFound");
				assertThat(errors.getFirst().getExtensions())
					.containsEntry("code", "COMMON_404")
					.containsEntry("traceId", TRACE_ID)
					.doesNotContainKey("context");
			});

		assertThat(output).contains("WARN", "[" + TRACE_ID + "]", "code=COMMON_404, context={applicationId=9}");
	}

	@Test
	@DisplayName("5xx BusinessException은 INTERNAL_ERROR로 바꾸고, 스택트레이스와 함께 ERROR 로그를 남긴다")
	void resolveServerBusinessException(CapturedOutput output) {
		withTraceId().document("{ testServerError }")
			.execute()
			.errors()
			.satisfy(errors -> {
				assertThat(errors.getFirst().getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR);
				assertThat(errors.getFirst().getExtensions()).containsEntry("code", "COMMON_500");
			});

		assertThat(output).contains("ERROR", "code=COMMON_500", "at com.mozip.global.exception");
	}

	@Test
	@DisplayName("예상하지 못한 예외는 내부 메시지를 숨기고 COMMON_500으로 바꾼다")
	void resolveUnexpectedException() {
		withTraceId().document("{ testUnexpected }")
			.execute()
			.errors()
			.satisfy(errors -> {
				assertThat(errors.getFirst().getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR);
				assertThat(errors.getFirst().getMessage()).isEqualTo("서버 내부 오류가 발생했습니다.");
				assertThat(errors.getFirst().getExtensions())
					.containsEntry("code", "COMMON_500")
					.containsEntry("traceId", TRACE_ID);
			});
	}

	@Test
	@DisplayName("인자 검증에 실패하면 BAD_REQUEST로 바꾸고, extensions에 필드별 에러 목록을 담는다")
	void resolveConstraintViolation() {
		withTraceId().document("{ testValidation(name: \"\") }")
			.execute()
			.errors()
			.satisfy(errors -> {
				assertThat(errors).hasSize(1);
				assertThat(errors.getFirst().getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
				assertThat(errors.getFirst().getMessage()).isEqualTo("name: 이름은 필수입니다.");
				assertThat(errors.getFirst().getExtensions())
					.containsEntry("code", "COMMON_400")
					.containsEntry("traceId", TRACE_ID);
				assertThat(errors.getFirst().getExtensions().get("fieldErrors"))
					.asList()
					.singleElement()
					.hasFieldOrPropertyWithValue("field", "name")
					.hasFieldOrPropertyWithValue("message", "이름은 필수입니다.");
			});
	}

	private HttpGraphQlTester withTraceId() {
		return graphQlTester.mutate()
			.header(TraceIdFilter.TRACE_ID_HEADER, TRACE_ID)
			.build();
	}

	// 다른 테스트 컨텍스트에 섞이지 않게 중첩 클래스로 두고 @Import
	@Controller
	static class TestController {

		@QueryMapping
		public String testNotFound() {
			throw new BusinessException(CommonErrorCode.NOT_FOUND).addContext("applicationId", 9);
		}

		@QueryMapping
		public String testServerError() {
			throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
		}

		@QueryMapping
		public String testValidation(@Argument @NotBlank(message = "이름은 필수입니다.") String name) {
			return name;
		}

		@QueryMapping
		public String testUnexpected() {
			throw new IllegalStateException("내부 구현 정보");
		}

	}

}
