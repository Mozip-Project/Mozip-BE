package com.mozip.global.security.graphql;

import java.util.Optional;

import com.mozip.global.exception.ErrorCode;

import graphql.GraphQLContext;

// GraphqlAuthInterceptor가 요청 스레드에서 읽은 인증 상태를 GraphQLContext로 전달
// 데이터 페처는 다른 스레드에서 실행될 수 있어 SecurityContextHolder 대신 이 값을 사용
public record GraphqlAuthContext(boolean authenticated, ErrorCode authError) {

	private static final String KEY = GraphqlAuthContext.class.getName();

	public void putInto(GraphQLContext context) {
		context.put(KEY, this);
	}

	public static Optional<GraphqlAuthContext> from(GraphQLContext context) {
		return Optional.ofNullable(context.get(KEY));
	}

}
