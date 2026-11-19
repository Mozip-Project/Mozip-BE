package com.mozip.global.config.graphql;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
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

// 엔티티의 LocalDateTime(서울 시각)을 오프셋을 붙인 ISO-8601로 주고받음
// 입력에 오프셋이 있으면 서울 시각으로 바꾸고, 없으면 서울 시각으로 간주
public final class LocalDateTimeScalar {

	public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	public static final GraphQLScalarType INSTANCE = GraphQLScalarType.newScalar()
		.name("DateTime")
		.description("서울 시각에 오프셋을 붙인 ISO-8601 (예: 2026-11-15T14:00:00+09:00)")
		.coercing(new LocalDateTimeCoercing())
		.build();

	private LocalDateTimeScalar() {
	}

	private static final class LocalDateTimeCoercing implements Coercing<LocalDateTime, String> {

		@Override
		public String serialize(Object value, GraphQLContext context, Locale locale) {
			if (value instanceof LocalDateTime localDateTime) {
				return format(localDateTime);
			}
			if (value instanceof OffsetDateTime offsetDateTime) {
				return format(offsetDateTime.atZoneSameInstant(ZONE).toLocalDateTime());
			}
			throw new CoercingSerializeException("DateTime으로 변환할 수 없는 값입니다: " + value);
		}

		@Override
		public LocalDateTime parseValue(Object input, GraphQLContext context, Locale locale) {
			if (input instanceof String text) {
				try {
					return parse(text);
				} catch (DateTimeParseException e) {
					throw new CoercingParseValueException("DateTime 형식이 아닙니다: " + text, e);
				}
			}
			throw new CoercingParseValueException("DateTime은 문자열이어야 합니다: " + input);
		}

		@Override
		public LocalDateTime parseLiteral(Value<?> input, CoercedVariables variables, GraphQLContext context,
			Locale locale) {
			if (input instanceof StringValue stringValue) {
				try {
					return parse(stringValue.getValue());
				} catch (DateTimeParseException e) {
					throw new CoercingParseLiteralException("DateTime 형식이 아닙니다: " + stringValue.getValue(), e);
				}
			}
			throw new CoercingParseLiteralException("DateTime은 문자열이어야 합니다: " + input);
		}

		@Override
		public Value<?> valueToLiteral(Object input, GraphQLContext context, Locale locale) {
			return StringValue.of(serialize(input, context, locale));
		}

		private static String format(LocalDateTime value) {
			return value.atZone(ZONE).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
		}

		private static LocalDateTime parse(String text) {
			TemporalAccessor parsed = DateTimeFormatter.ISO_DATE_TIME.parseBest(text, OffsetDateTime::from,
				LocalDateTime::from);
			if (parsed instanceof OffsetDateTime offsetDateTime) {
				return offsetDateTime.atZoneSameInstant(ZONE).toLocalDateTime();
			}
			return (LocalDateTime)parsed;
		}

	}

}
