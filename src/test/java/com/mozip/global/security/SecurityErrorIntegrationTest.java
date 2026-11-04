package com.mozip.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.mozip.TestcontainersConfiguration;
import com.mozip.global.logging.TraceIdFilter;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SecurityErrorIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("인증 없이 보호된 API를 호출하면 traceId가 담긴 ApiResponse 형식의 401로 응답한다")
	void unauthorized() throws Exception {
		mockMvc.perform(get("/api/protected").header(TraceIdFilter.TRACE_ID_HEADER, "front-abc-1"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(TraceIdFilter.TRACE_ID_HEADER, "front-abc-1"))
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value("COMMON_401"))
			.andExpect(jsonPath("$.traceId").value("front-abc-1"));
	}

}
