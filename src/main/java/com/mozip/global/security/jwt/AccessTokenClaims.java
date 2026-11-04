package com.mozip.global.security.jwt;

import com.mozip.domain.user.entity.Authority;

public record AccessTokenClaims(Long userId, Authority authority) {
}
