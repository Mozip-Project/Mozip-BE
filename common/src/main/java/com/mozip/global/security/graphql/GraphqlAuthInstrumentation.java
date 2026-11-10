package com.mozip.global.security.graphql;

import org.springframework.stereotype.Component;

import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;
import com.mozip.global.exception.ErrorCode;

import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;

// GraphQL은 URL이 하나라 Security URL 규칙 대신 최상위 필드(Query/Mutation/Subscription) 단위로 인증 검사
// 기본은 인증 필요이며 스키마에 @public을 붙인 필드만 로그인 없이 호출 가능
// 거부 시 BusinessException을 던져 GraphqlExceptionResolver가 REST와 같은 코드(AUTH_002, AUTH_003, COMMON_401)로 응답
@Component
public class GraphqlAuthInstrumentation extends SimplePerformantInstrumentation {

	public static final String PUBLIC_DIRECTIVE = "public";

	@Override
	public DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher,
		InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
		DataFetchingEnvironment environment = parameters.getEnvironment();
		GraphQLFieldDefinition fieldDefinition = environment.getFieldDefinition();
		if (!isRootField(environment) || isIntrospection(fieldDefinition) || isPublic(fieldDefinition)) {
			return dataFetcher;
		}
		GraphqlAuthContext authContext = GraphqlAuthContext.from(environment.getGraphQlContext())
			.orElseGet(() -> new GraphqlAuthContext(false, null));
		if (authContext.authenticated()) {
			return dataFetcher;
		}
		ErrorCode errorCode = authContext.authError() != null ? authContext.authError() : CommonErrorCode.UNAUTHORIZED;
		return env -> {
			throw new BusinessException(errorCode);
		};
	}

	// 하위 필드는 최상위 필드를 통과해야만 도달하므로 최상위만 검사
	private static boolean isRootField(DataFetchingEnvironment environment) {
		GraphQLSchema schema = environment.getGraphQLSchema();
		GraphQLObjectType parentType = environment.getExecutionStepInfo().getObjectType();
		return parentType == schema.getQueryType()
			|| parentType == schema.getMutationType()
			|| parentType == schema.getSubscriptionType();
	}

	private static boolean isPublic(GraphQLFieldDefinition fieldDefinition) {
		return fieldDefinition.getAppliedDirective(PUBLIC_DIRECTIVE) != null;
	}

	// __schema, __type, __typename은 데이터가 아닌 스키마 조회라 로그인 없이 허용 (GraphiQL 자동완성, 프론트 codegen)
	// 운영에서는 GraphqlIntrospectionInterceptor가 introspection 자체를 끔
	private static boolean isIntrospection(GraphQLFieldDefinition fieldDefinition) {
		return fieldDefinition.getName().startsWith("__");
	}

}
