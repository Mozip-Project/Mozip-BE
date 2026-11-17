package com.mozip.global.health;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.HttpGraphQlTester;

import com.mozip.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureHttpGraphQlTester
class HealthGraphqlControllerTest {

	@Autowired
	private HttpGraphQlTester graphQlTester;

	@Test
	@DisplayName("로그인 없이 health Query를 호출하면 UP을 응답한다")
	void health() {
		graphQlTester.document("{ health { status } }")
			.execute()
			.path("health.status")
			.entity(String.class)
			.isEqualTo("UP");
	}

}
