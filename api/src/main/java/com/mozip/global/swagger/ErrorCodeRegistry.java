package com.mozip.global.swagger;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import com.mozip.global.exception.ErrorCode;

// com.mozip 하위의 모든 ErrorCode enum 상수를 코드 문자열로 찾을 수 있게 모은 레지스트리
@Component
public class ErrorCodeRegistry {

	private static final String BASE_PACKAGE = "com.mozip";

	private final Map<String, ErrorCode> errorCodes = scanErrorCodes();

	public ErrorCode get(String code) {
		ErrorCode errorCode = errorCodes.get(code);
		if (errorCode == null) {
			throw new IllegalStateException("존재하지 않는 에러 코드입니다: " + code);
		}
		return errorCode;
	}

	private static Map<String, ErrorCode> scanErrorCodes() {
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(new AssignableTypeFilter(ErrorCode.class));

		Map<String, ErrorCode> result = new HashMap<>();
		for (BeanDefinition candidate : scanner.findCandidateComponents(BASE_PACKAGE)) {
			Class<?> type = ClassUtils.resolveClassName(candidate.getBeanClassName(), ClassUtils.getDefaultClassLoader());
			if (!type.isEnum()) {
				continue;
			}
			for (Object constant : type.getEnumConstants()) {
				ErrorCode errorCode = (ErrorCode)constant;
				ErrorCode duplicated = result.putIfAbsent(errorCode.getCode(), errorCode);
				if (duplicated != null) {
					// 중복된 에러 코드가 발견되면 앱이 시작되지 않도록 예외 발생
					throw new IllegalStateException("중복된 에러 코드입니다: " + errorCode.getCode()
						+ " (" + duplicated.getClass().getSimpleName() + ", " + type.getSimpleName() + ")");
				}
			}
		}
		return Map.copyOf(result);
	}

}
