package com.mozip.global.security.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.HttpGraphQlTester;

import com.mozip.TestcontainersConfiguration;

// 운영 프로필과 같은 설정(graphql.introspection-enabled=false)에서의 동작 검증
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "graphql.introspection-enabled=false")
@AutoConfigureMockMvc
@AutoConfigureHttpGraphQlTester
class GraphqlIntrospectionDisabledTest {

	@Autowired
	private HttpGraphQlTester graphQlTester;

	@Test
	@DisplayName("introspection을 끄면 __schema 조회는 거부되고 일반 쿼리는 그대로 동작한다")
	void introspectionDisabled() {
		graphQlTester.document("{ __schema { queryType { name } } }")
			.execute()
			.errors()
			.satisfy(errors -> {
				assertThat(errors).hasSize(1);
				assertThat(errors.getFirst().getMessage()).containsIgnoringCase("introspection");
			});

		graphQlTester.document("{ health { status } }")
			.execute()
			.path("health.status").hasValue();
	}

}
