package com.mozip.global.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mozip.global.common.response.FieldErrorResponse;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;
	// 같은 에러 코드 안에서 상황을 나누는 enum 이름, 없으면 null
	private final String reason;
	private final Map<String, Object> context = new LinkedHashMap<>();
	private List<FieldErrorResponse> fieldErrors = List.of();

	public BusinessException(ErrorCode errorCode) {
		this(errorCode, null);
	}

	// 같은 코드 안에서 프론트가 분기해야 하는 상황 구분 (예: POSTING_409의 NOT_DRAFT와 ALREADY_PUBLISHED)
	// extensions.reason에 enum 이름이 들어감 (error.md)
	public BusinessException(ErrorCode errorCode, Enum<?> reason) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
		this.reason = reason == null ? null : reason.name();
	}

	// 로그에만 남길 추가 정보이며 응답에는 포함하지 않음 (예: addContext("applicationId", id))
	public BusinessException addContext(String key, Object value) {
		context.put(key, value);
		return this;
	}

	public Map<String, Object> getContext() {
		return Collections.unmodifiableMap(context);
	}

	// Bean Validation으로 표현하기 어려운 서비스 레벨 검증(예: 발행 전 단계 누락)이 어느 필드/단계가 문제인지
	// 응답에 실어 보낼 때 씀. addContext와 달리 이건 응답(extensions.fieldErrors 또는 REST의 data)에 포함됨
	public BusinessException withFieldErrors(List<FieldErrorResponse> fieldErrors) {
		this.fieldErrors = fieldErrors;
		return this;
	}

}
