package com.mozip.global.config.graphql;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;

// ISO-8601 날짜(yyyy-MM-dd)를 LocalDate로 주고받음
// 2026-02-30처럼 없는 날짜는 말일로 보정하지 않고 거부 (ISO_LOCAL_DATE는 STRICT 해석)
public final class LocalDateScalar {

	public static final GraphQLScalarType INSTANCE = GraphQLScalarType.newScalar()
		.name("Date")
		.description("ISO-8601 날짜 (예: 2026-11-15)")
		.coercing(new LocalDateCoercing())
		.build();

	private LocalDateScalar() {
	}

	private static final class LocalDateCoercing implements Coercing<LocalDate, String> {

		@Override
		public String serialize(Object value, GraphQLContext context, Locale locale) {
			if (value instanceof LocalDate localDate) {
				return localDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
			}
			throw new CoercingSerializeException("Date로 변환할 수 없는 값입니다: " + value);
		}

		@Override
		public LocalDate parseValue(Object input, GraphQLContext context, Locale locale) {
			if (input instanceof String text) {
				try {
					return parse(text);
				} catch (DateTimeParseException e) {
					throw new CoercingParseValueException("Date 형식이 아니거나 없는 날짜입니다: " + text, e);
				}
			}
			throw new CoercingParseValueException("Date는 문자열이어야 합니다: " + input);
		}

		@Override
		public LocalDate parseLiteral(Value<?> input, CoercedVariables variables, GraphQLContext context,
			Locale locale) {
			if (input instanceof StringValue stringValue) {
				try {
					return parse(stringValue.getValue());
				} catch (DateTimeParseException e) {
					throw new CoercingParseLiteralException(
						"Date 형식이 아니거나 없는 날짜입니다: " + stringValue.getValue(), e);
				}
			}
			throw new CoercingParseLiteralException("Date는 문자열이어야 합니다: " + input);
		}

		@Override
		public Value<?> valueToLiteral(Object input, GraphQLContext context, Locale locale) {
			return StringValue.of(serialize(input, context, locale));
		}

		private static LocalDate parse(String text) {
			return LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE);
		}

	}

}
