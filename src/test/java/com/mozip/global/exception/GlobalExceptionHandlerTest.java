package com.mozip.global.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mozip.global.logging.TraceIdFilter;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

	private static final String TRACE_ID = "test-trace-id";

	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
		.setControllerAdvice(new GlobalExceptionHandler())
		.addFilters(new TraceIdFilter())
		.build();

	@Test
	@DisplayName("4xx BusinessException은 실패 응답으로 바꾸고, context와 함께 WARN 로그를 남긴다")
	void handleClientBusinessException(CapturedOutput output) throws Exception {
		mockMvc.perform(get("/test/not-found").header(TraceIdFilter.TRACE_ID_HEADER, TRACE_ID))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value("COMMON_404"))
			.andExpect(jsonPath("$.message").value("요청한 리소스를 찾을 수 없습니다."))
			.andExpect(jsonPath("$.data").value(nullValue()))
			.andExpect(jsonPath("$.traceId").value(TRACE_ID))
			.andExpect(jsonPath("$.context").doesNotExist());

		assertThat(output).contains("WARN", "code=COMMON_404, context={applicationId=9}");
		assertThat(output).doesNotContain("ERROR");
	}

	@Test
	@DisplayName("5xx BusinessException은 스택트레이스와 함께 ERROR 로그를 남긴다")
	void handleServerBusinessException(CapturedOutput output) throws Exception {
		mockMvc.perform(get("/test/server-error"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("COMMON_500"));

		assertThat(output).contains("ERROR", "code=COMMON_500", "at com.mozip.global.exception");
	}

	@Test
	@DisplayName("요청 값 검증에 실패하면 400과 필드 에러 메시지로 응답한다")
	void handleValidationException() throws Exception {
		mockMvc.perform(post("/test/validation")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON_400"))
			.andExpect(jsonPath("$.message").value("name: 이름은 필수입니다."));
	}

	@Test
	@DisplayName("요청 본문을 읽을 수 없으면 400으로 응답한다")
	void handleNotReadableException() throws Exception {
		mockMvc.perform(post("/test/validation")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{invalid json"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON_400"));
	}

	@Test
	@DisplayName("지원하지 않는 HTTP 메서드는 405로 응답한다")
	void handleMethodNotSupportedException() throws Exception {
		mockMvc.perform(post("/test/not-found"))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(jsonPath("$.code").value("COMMON_405"));
	}

	@Test
	@DisplayName("필수 요청 파라미터가 없으면 500이 아니라 400으로 응답하고 ERROR 로그를 남기지 않는다")
	void handleMissingRequestParameter(CapturedOutput output) throws Exception {
		mockMvc.perform(get("/test/param"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value("COMMON_400"))
			.andExpect(jsonPath("$.message").value("잘못된 요청입니다."));

		assertThat(output).doesNotContain("ERROR");
	}

	@Test
	@DisplayName("요청 파라미터 타입이 맞지 않으면 400으로 응답한다")
	void handleTypeMismatch() throws Exception {
		mockMvc.perform(get("/test/param").param("page", "abc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON_400"));
	}

	@Test
	@DisplayName("경로 변수 검증에 실패하면 400으로 응답한다")
	void handleHandlerMethodValidation() throws Exception {
		mockMvc.perform(get("/test/path/-1"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON_400"));
	}

	@Test
	@DisplayName("서비스 계층 검증에 실패하면 400과 첫 번째 위반 메시지로 응답한다")
	void handleConstraintViolation() throws Exception {
		mockMvc.perform(get("/test/constraint-violation"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON_400"))
			.andExpect(jsonPath("$.message").value("name: 이름은 필수입니다."));
	}

	@Test
	@DisplayName("지원하지 않는 Content-Type은 415로 응답한다")
	void handleMediaTypeNotSupported() throws Exception {
		mockMvc.perform(post("/test/validation")
				.contentType(MediaType.TEXT_PLAIN)
				.content("name"))
			.andExpect(status().isUnsupportedMediaType())
			.andExpect(jsonPath("$.code").value("COMMON_415"))
			.andExpect(jsonPath("$.traceId").exists());
	}

	@Test
	@DisplayName("예상하지 못한 예외는 내부 메시지를 숨기고 500으로 응답한다")
	void handleUnexpectedException() throws Exception {
		mockMvc.perform(get("/test/unexpected"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("COMMON_500"))
			.andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));
	}

	@RestController
	static class TestController {

		@GetMapping("/test/not-found")
		void notFound() {
			throw new BusinessException(CommonErrorCode.NOT_FOUND).addContext("applicationId", 9);
		}

		@GetMapping("/test/server-error")
		void serverError() {
			throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
		}

		@PostMapping("/test/validation")
		void validation(@Valid @RequestBody TestRequest request) {
		}

		@GetMapping("/test/param")
		void param(@RequestParam int page) {
		}

		@GetMapping("/test/path/{id}")
		void path(@PathVariable @Positive Long id) {
		}

		@GetMapping("/test/constraint-violation")
		void constraintViolation() {
			try (var factory = Validation.buildDefaultValidatorFactory()) {
				throw new ConstraintViolationException(factory.getValidator().validate(new TestRequest("")));
			}
		}

		@GetMapping("/test/unexpected")
		void unexpected() {
			throw new IllegalStateException("내부 구현 정보");
		}

	}

	record TestRequest(@NotBlank(message = "이름은 필수입니다.") String name) {
	}

}
