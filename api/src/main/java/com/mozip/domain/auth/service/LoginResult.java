package com.mozip.domain.auth.service;

import com.mozip.domain.auth.dto.LoginResponse;

// tokens는 쿠키로, user는 응답 body로 반환
public record LoginResult(AuthTokens tokens, LoginResponse user) {
}
