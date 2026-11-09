package com.mozip.global.swagger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mozip.global.exception.CommonErrorCode;

class ErrorCodeRegistryTest {

	private final ErrorCodeRegistry errorCodeRegistry = new ErrorCodeRegistry();

	@Test
	@DisplayName("코드 문자열로 ErrorCode enum 상수를 찾는다")
	void get() {
		assertThat(errorCodeRegistry.get("COMMON_400")).isEqualTo(CommonErrorCode.INVALID_INPUT);
		assertThat(errorCodeRegistry.get("COMMON_500")).isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR);
	}

	@Test
	@DisplayName("존재하지 않는 코드면 예외가 발생한다")
	void getUnknownCode() {
		assertThatThrownBy(() -> errorCodeRegistry.get("COMMON_999"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("COMMON_999");
	}

}
