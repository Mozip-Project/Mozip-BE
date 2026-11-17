package com.mozip.domain.auth.exception;

import com.mozip.global.exception.BusinessException;

// 리프레시 토큰을 이미 소비(폐기)한 뒤에 재발급이 실패한 경우
// 같은 쿠키로 재시도해도 복구되지 않으므로 컨트롤러가 쿠키를 지우는 기준으로 사용 (응답 코드는 원인 예외 그대로)
public class RefreshTokenConsumedException extends BusinessException {

	public RefreshTokenConsumedException(BusinessException cause) {
		super(cause.getErrorCode());
		cause.getContext().forEach(this::addContext);
		initCause(cause);
	}

}
