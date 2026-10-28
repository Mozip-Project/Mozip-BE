package com.mozip.global.common.response;

import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;

// 검증 실패 필드 하나이며, 클래스 단위 검증처럼 특정 필드가 없으면 field는 null
public record FieldErrorResponse(String field, String message) {

	// 메서드 이름 노드는 빼고 파라미터부터 표시 (예: createClub.request.name → request.name)
	public static FieldErrorResponse from(ConstraintViolation<?> violation) {
		String field = StreamSupport.stream(violation.getPropertyPath().spliterator(), false)
			.filter(node -> node.getKind() != ElementKind.METHOD && node.getKind() != ElementKind.CONSTRUCTOR)
			.map(Path.Node::toString)
			.collect(Collectors.joining("."));
		return new FieldErrorResponse(field.isEmpty() ? null : field, violation.getMessage());
	}

	public String toMessage() {
		return field == null ? message : field + ": " + message;
	}

}
