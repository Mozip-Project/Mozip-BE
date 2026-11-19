package com.mozip.global.config.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;

// DB 없이 메인 스키마 파일(graphql/*.graphqls)과 scalar 등록만 검증
class GraphqlSchemaTest {

	@SuppressWarnings("unchecked")
	private final Coercing<LocalDateTime, String> dateTime =
		(Coercing<LocalDateTime, String>)LocalDateTimeScalar.INSTANCE.getCoercing();

	@SuppressWarnings("unchecked")
	private final Coercing<LocalDate, String> date =
		(Coercing<LocalDate, String>)LocalDateScalar.INSTANCE.getCoercing();

	@Test
	@DisplayName("메인 스키마 파일을 합쳐 실행 가능한 스키마를 만들 수 있다")
	void buildSchema() throws IOException {
		TypeDefinitionRegistry registry = new TypeDefinitionRegistry();
		SchemaParser parser = new SchemaParser();
		for (Resource resource : new PathMatchingResourcePatternResolver().getResources("classpath:graphql/*.graphqls")) {
			try (InputStream input = resource.getInputStream()) {
				registry.merge(parser.parse(input));
			}
		}
		RuntimeWiring.Builder wiring = RuntimeWiring.newRuntimeWiring();
		new GraphqlScalarConfig().scalarWiringConfigurer().configure(wiring);

		GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(registry, wiring.build());

		assertThat(schema.getObjectType("Posting")).isNotNull();
		assertThat(schema.getObjectType("Application").getFieldDefinition("availableTimes")).isNotNull();
	}

	@Test
	@DisplayName("DateTime은 서울 오프셋을 붙여 내보낸다")
	void serializeDateTime() {
		String serialized = dateTime.serialize(LocalDateTime.of(2026, 11, 15, 14, 0), GraphQLContext.getDefault(),
			Locale.KOREA);

		assertThat(serialized).isEqualTo("2026-11-15T14:00:00+09:00");
	}

	@Test
	@DisplayName("DateTime 입력은 오프셋이 있으면 서울 시각으로 바꾸고, 없으면 서울 시각으로 간주한다")
	void parseDateTime() {
		GraphQLContext context = GraphQLContext.getDefault();

		assertThat(dateTime.parseValue("2026-11-15T05:00:00Z", context, Locale.KOREA))
			.isEqualTo(LocalDateTime.of(2026, 11, 15, 14, 0));
		assertThat(dateTime.parseValue("2026-11-15T14:00:00", context, Locale.KOREA))
			.isEqualTo(LocalDateTime.of(2026, 11, 15, 14, 0));
		assertThatThrownBy(() -> dateTime.parseValue("2026-11-15", context, Locale.KOREA))
			.isInstanceOf(CoercingParseValueException.class);
	}

	@Test
	@DisplayName("Date는 올바른 날짜만 받고, 없는 날짜는 변수와 리터럴 모두 거부한다")
	void parseDate() {
		GraphQLContext context = GraphQLContext.getDefault();

		assertThat(date.parseValue("2028-02-29", context, Locale.KOREA)).isEqualTo(LocalDate.of(2028, 2, 29));
		assertThat(date.serialize(LocalDate.of(2026, 11, 15), context, Locale.KOREA)).isEqualTo("2026-11-15");
		assertThatThrownBy(() -> date.parseValue("2026-02-30", context, Locale.KOREA))
			.isInstanceOf(CoercingParseValueException.class);
		assertThatThrownBy(() -> date.parseLiteral(StringValue.of("2026-02-30"), CoercedVariables.emptyVariables(),
			context, Locale.KOREA))
			.isInstanceOf(CoercingParseLiteralException.class);
	}

}
