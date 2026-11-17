package com.mozip.global.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;
	private final Map<String, Object> context = new LinkedHashMap<>();

	public BusinessException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
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
