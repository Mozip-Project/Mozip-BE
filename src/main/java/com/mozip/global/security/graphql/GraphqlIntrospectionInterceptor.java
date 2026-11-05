package com.mozip.global.security.graphql;

import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.stereotype.Component;

import graphql.introspection.Introspection;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

// graphql.introspection-enabled=false면 요청마다 GraphQLContext에 graphql-java의 비활성화 플래그를 넣음
// JVM 전역 설정(Introspection.enabledJvmWide) 대신 요청 단위로 두어 테스트끼리 영향을 주지 않게 함
@Component
@RequiredArgsConstructor
public class GraphqlIntrospectionInterceptor implements WebGraphQlInterceptor {

	private final GraphqlProperties properties;

	@Override
	public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
		if (!properties.introspectionEnabled()) {
			request.configureExecutionInput((input, builder) -> {
				input.getGraphQLContext().put(Introspection.INTROSPECTION_DISABLED, true);
				return input;
			});
		}
		return chain.next(request);
	}

}
