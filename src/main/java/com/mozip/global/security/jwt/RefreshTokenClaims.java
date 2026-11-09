package com.mozip.global.security.jwt;

// tokenId(jti)로 Redis의 리프레시 토큰 조회
public record RefreshTokenClaims(Long userId, String tokenId) {
}
