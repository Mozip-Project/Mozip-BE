package com.mozip.global.security.jwt;

import org.springframework.http.HttpStatus;

import com.mozip.global.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 토큰 검증과 저장소 관련 에러 (로그인 자체의 에러는 domain.auth의 AuthErrorCode)
@Getter
@RequiredArgsConstructor
public enum TokenErrorCode implements ErrorCode {

	// 이 코드를 받으면 /auth/reissue 호출 후 원래 요청 재시도
	EXPIRED_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_002", "액세스 토큰이 만료되었습니다."),
	INVALID_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_003", "유효하지 않은 액세스 토큰입니다."),
	// 재발급 실패 (만료, 위조, 이미 사용됨, 로그아웃됨) 시 다시 로그인
	INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_004", "로그인이 만료되었습니다. 다시 로그인해 주세요."),
	// Redis 장애로 토큰을 저장하거나 확인할 수 없을 때 (잠시 후 재시도 안내)
	TOKEN_STORE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_006", "로그인 처리를 잠시 할 수 없습니다. 잠시 후 다시 시도해 주세요.");

	private final HttpStatus status;
	private final String code;
	private final String message;

}
