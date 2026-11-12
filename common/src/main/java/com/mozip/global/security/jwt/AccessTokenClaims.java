package com.mozip.global.security.jwt;

import com.mozip.global.security.Authority;

public record AccessTokenClaims(Long userId, Authority authority) {
}
