package com.mozip.global.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;
	// 같은 에러 코드 안에서 상황을 나눌 때 프론트 분기용으로 응답에 담는 enum 이름 (없으면 null)
	private final String reason;
	private final Map<String, Object> context = new LinkedHashMap<>();

	public BusinessException(ErrorCode errorCode) {
		this(errorCode, null);
	}

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

}
