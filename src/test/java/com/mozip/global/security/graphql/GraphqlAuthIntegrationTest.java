package com.mozip.global.security.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import com.mozip.TestcontainersConfiguration;
import com.mozip.global.security.Authority;
import com.mozip.global.security.cookie.AuthCookieManager;
import com.mozip.global.security.jwt.JwtProperties;
import com.mozip.global.security.jwt.JwtProvider;

@Import({TestcontainersConfiguration.class, GraphqlAuthIntegrationTest.TestController.class})
@SpringBootTest(properties = "spring.graphql.schema.locations=classpath:graphql/,classpath:graphql-auth-test/")
@AutoConfigureMockMvc
@AutoConfigureHttpGraphQlTester
class GraphqlAuthIntegrationTest {

	@Autowired
	private HttpGraphQlTester graphQlTester;

	@Autowired
	private JwtProvider jwtProvider;

	@Autowired
	private JwtProperties jwtProperties;

	@Test
	@DisplayName("@public 필드는 토큰 없이 호출할 수 있다")
	void publicField() {
		graphQlTester.document("{ testPublic }")
			.execute()
			.path("testPublic").entity(String.class).isEqualTo("public");
	}

	@Test
	@DisplayName("introspection 쿼리는 토큰 없이 호출할 수 있다 (GraphiQL 자동완성, 프론트 codegen)")
	void introspectionWithoutToken() {
		graphQlTester.document("{ __schema { queryType { name } } }")
			.execute()
			.path("__schema.queryType.name").entity(String.class).isEqualTo("Query");
		graphQlTester.document("{ __typename }")
			.execute()
			.path("__typename").entity(String.class).isEqualTo("Query");
	}

	@Test
	@DisplayName("@public이 없는 필드는 토큰 없이 호출하면 COMMON_401로 거부한다")
	void securedFieldWithoutToken() {
		assertError(graphQlTester, "{ testSecured }", ErrorType.UNAUTHORIZED, "COMMON_401");
	}

	@Test
	@DisplayName("만료된 토큰이면 REST와 같은 AUTH_002로 거부해 프론트가 재발급을 시도할 수 있다")
	void expiredToken() {
		JwtProvider expiredProvider = new JwtProvider(new JwtProperties(jwtProperties.secret(),
			Duration.ofSeconds(-1), jwtProperties.refreshTokenExpiration()));

		assertError(withToken(expiredProvider.createAccessToken(1L, Authority.USER)), "{ testSecured }",
			ErrorType.UNAUTHORIZED, "AUTH_002");
	}

	@Test
	@DisplayName("위조된 토큰이면 AUTH_003으로 거부한다")
	void forgedToken() {
		assertError(withToken("forged"), "{ testSecured }", ErrorType.UNAUTHORIZED, "AUTH_003");
	}

	@Test
	@DisplayName("유효한 토큰이면 통과하고, @PreAuthorize 권한이 없으면 COMMON_403으로 거부한다")
	void authorized() {
		HttpGraphQlTester user = withToken(jwtProvider.createAccessToken(1L, Authority.USER));
		user.document("{ testSecured }")
			.execute()
			.path("testSecured").entity(String.class).isEqualTo("secured");
		assertError(user, "{ testAdminOnly }", ErrorType.FORBIDDEN, "COMMON_403");

		withToken(jwtProvider.createAccessToken(2L, Authority.ADMIN)).document("{ testAdminOnly }")
			.execute()
			.path("testAdminOnly").entity(String.class).isEqualTo("admin");
	}

	private HttpGraphQlTester withToken(String accessToken) {
		// MockMvc는 Cookie 헤더 문자열을 파싱하지 않으므로 WebTestClient의 쿠키로 전달
		return graphQlTester.mutate()
			.webTestClient(client -> client.defaultCookie(AuthCookieManager.ACCESS_TOKEN_COOKIE, accessToken))
			.build();
	}

	private static void assertError(HttpGraphQlTester tester, String document, ErrorType errorType, String code) {
		tester.document(document)
			.execute()
			.errors()
			.satisfy(errors -> {
				assertThat(errors).hasSize(1);
				assertThat(errors.getFirst().getErrorType()).isEqualTo(errorType);
				assertThat(errors.getFirst().getExtensions()).containsEntry("code", code);
			});
	}

	@Controller
	static class TestController {

		@QueryMapping
		public String testPublic() {
			return "public";
		}

		@QueryMapping
		public String testSecured() {
			return "secured";
		}

		@PreAuthorize("hasRole('ADMIN')")
		@QueryMapping
		public String testAdminOnly() {
			return "admin";
		}

	}

}
