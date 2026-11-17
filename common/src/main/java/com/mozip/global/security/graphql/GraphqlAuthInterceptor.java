package com.mozip.global.security.graphql;

import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.mozip.global.exception.ErrorCode;
import com.mozip.global.security.AuthUser;
import com.mozip.global.security.jwt.JwtAuthenticationFilter;

import reactor.core.publisher.Mono;

// /graphql 요청마다 JwtAuthenticationFilter의 결과(인증 여부, 토큰 검증 실패 사유)를 GraphQLContext에 담음
@Component
public class GraphqlAuthInterceptor implements WebGraphQlInterceptor {

	@Override
	public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		boolean authenticated = authentication != null && authentication.getPrincipal() instanceof AuthUser;
		ErrorCode authError = request.getAttributes().get(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE)
			instanceof ErrorCode errorCode ? errorCode : null;
		// 기존 GraphQLContext에 추가 (builder.graphQLContext는 컨텍스트를 통째로 교체하므로 사용하지 않음)
		request.configureExecutionInput((input, builder) -> {
			new GraphqlAuthContext(authenticated, authError).putInto(input.getGraphQLContext());
			return input;
		});
		return chain.next(request);
	}

}
