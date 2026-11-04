package com.mozip.global.security.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;
import java.util.function.Supplier;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.mozip.global.security.Authority;
import com.mozip.global.exception.BusinessException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

// HS256 JWT 발급, 검증
// 검증 실패 시 TokenErrorCode로 BusinessException 발생
@Component
public class JwtProvider {

	private static final String CLAIM_TOKEN_TYPE = "token_type";
	private static final String CLAIM_AUTHORITY = "authority";

	private final SecretKey key;
	private final JwtParser parser;
	private final JwtProperties properties;

	public JwtProvider(JwtProperties properties) {
		// 32바이트(256비트) 미만일 경우 WeakKeyException
		this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
		this.parser = Jwts.parser().verifyWith(key).build();
		this.properties = properties;
	}

	public String createAccessToken(Long userId, Authority authority) {
		return Jwts.builder()
			.subject(String.valueOf(userId))
			.claim(CLAIM_TOKEN_TYPE, TokenType.ACCESS.name())
			.claim(CLAIM_AUTHORITY, authority.name())
			.issuedAt(new Date())
			.expiration(expiresAt(properties.accessTokenExpiration()))
			.signWith(key)
			.compact();
	}

	public IssuedRefreshToken createRefreshToken(Long userId) {
		String tokenId = UUID.randomUUID().toString();
		String token = Jwts.builder()
			.id(tokenId)
			.subject(String.valueOf(userId))
			.claim(CLAIM_TOKEN_TYPE, TokenType.REFRESH.name())
			.issuedAt(new Date())
			.expiration(expiresAt(properties.refreshTokenExpiration()))
			.signWith(key)
			.compact();
		return new IssuedRefreshToken(token, tokenId);
	}

	public AccessTokenClaims parseAccessToken(String token) {
		Claims claims = parse(token, TokenType.ACCESS,
			TokenErrorCode.EXPIRED_ACCESS_TOKEN, TokenErrorCode.INVALID_ACCESS_TOKEN);
		return convert(() -> new AccessTokenClaims(
			Long.valueOf(claims.getSubject()),
			Authority.valueOf(claims.get(CLAIM_AUTHORITY, String.class))), TokenErrorCode.INVALID_ACCESS_TOKEN);
	}

	public RefreshTokenClaims parseRefreshToken(String token) {
		Claims claims = parse(token, TokenType.REFRESH,
			TokenErrorCode.INVALID_REFRESH_TOKEN, TokenErrorCode.INVALID_REFRESH_TOKEN);
		return convert(() -> new RefreshTokenClaims(Long.valueOf(claims.getSubject()), claims.getId()),
			TokenErrorCode.INVALID_REFRESH_TOKEN);
	}

	// 서명은 맞지만 클레임 형식이 다른 토큰(배포 중 enum 이름 변경 등)도 500이 아닌 401로 응답
	private static <T> T convert(Supplier<T> converter, TokenErrorCode invalidError) {
		try {
			return converter.get();
		} catch (IllegalArgumentException | NullPointerException e) {
			throw new BusinessException(invalidError);
		}
	}

	private Claims parse(String token, TokenType expectedType, TokenErrorCode expiredError,
		TokenErrorCode invalidError) {
		Claims claims;
		try {
			claims = parser.parseSignedClaims(token).getPayload();
		} catch (ExpiredJwtException e) {
			throw new BusinessException(expiredError);
		} catch (JwtException | IllegalArgumentException e) {
			throw new BusinessException(invalidError);
		}
		if (!expectedType.name().equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
			throw new BusinessException(invalidError);
		}
		return claims;
	}

	private Date expiresAt(Duration expiration) {
		return new Date(System.currentTimeMillis() + expiration.toMillis());
	}

}
