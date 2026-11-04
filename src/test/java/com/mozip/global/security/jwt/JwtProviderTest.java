package com.mozip.global.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mozip.global.exception.BusinessException;
import com.mozip.global.security.Authority;

class JwtProviderTest {

	private static final String SECRET = "test-only-mozip-jwt-secret-key-0123456789abcdef";

	private final JwtProvider jwtProvider = provider(SECRET, Duration.ofMinutes(30), Duration.ofDays(14));

	@Test
	@DisplayName("액세스 토큰을 발급하고 검증하면 회원 ID와 권한을 꺼낼 수 있다")
	void accessToken() {
		String token = jwtProvider.createAccessToken(1L, Authority.USER);

		AccessTokenClaims claims = jwtProvider.parseAccessToken(token);

		assertThat(claims.userId()).isEqualTo(1L);
		assertThat(claims.authority()).isEqualTo(Authority.USER);
	}

	@Test
	@DisplayName("리프레시 토큰을 발급하고 검증하면 회원 ID와 tokenId를 꺼낼 수 있다")
	void refreshToken() {
		IssuedRefreshToken issued = jwtProvider.createRefreshToken(1L);

		RefreshTokenClaims claims = jwtProvider.parseRefreshToken(issued.token());

		assertThat(claims.userId()).isEqualTo(1L);
		assertThat(claims.tokenId()).isEqualTo(issued.tokenId());
	}

	@Test
	@DisplayName("만료된 액세스 토큰은 AUTH_002로 거부한다")
	void expiredAccessToken() {
		JwtProvider expiredProvider = provider(SECRET, Duration.ofSeconds(-1), Duration.ofDays(14));
		String token = expiredProvider.createAccessToken(1L, Authority.USER);

		assertErrorCode(() -> jwtProvider.parseAccessToken(token), TokenErrorCode.EXPIRED_ACCESS_TOKEN);
	}

	@Test
	@DisplayName("다른 키로 서명된 액세스 토큰은 AUTH_003으로 거부한다")
	void forgedAccessToken() {
		JwtProvider otherProvider = provider("other-secret-key-for-forged-token-0123456789", Duration.ofMinutes(30),
			Duration.ofDays(14));
		String token = otherProvider.createAccessToken(1L, Authority.ADMIN);

		assertErrorCode(() -> jwtProvider.parseAccessToken(token), TokenErrorCode.INVALID_ACCESS_TOKEN);
	}

	@Test
	@DisplayName("형식이 잘못된 토큰은 AUTH_003으로 거부하고 로그용 컨텍스트에 원인 예외를 남긴다")
	void malformedAccessToken() {
		assertThatThrownBy(() -> jwtProvider.parseAccessToken("not-a-jwt"))
			.isInstanceOf(BusinessException.class)
			.satisfies(e -> {
				assertThat(((BusinessException)e).getErrorCode()).isEqualTo(TokenErrorCode.INVALID_ACCESS_TOKEN);
				assertThat(((BusinessException)e).getContext()).containsEntry("cause", "MalformedJwtException");
			});
	}

	@Test
	@DisplayName("리프레시 토큰을 액세스 토큰으로 쓰거나 그 반대로 쓰면 거부한다")
	void tokenTypeMismatch() {
		String refreshToken = jwtProvider.createRefreshToken(1L).token();
		String accessToken = jwtProvider.createAccessToken(1L, Authority.USER);

		assertErrorCode(() -> jwtProvider.parseAccessToken(refreshToken), TokenErrorCode.INVALID_ACCESS_TOKEN);
		assertErrorCode(() -> jwtProvider.parseRefreshToken(accessToken), TokenErrorCode.INVALID_REFRESH_TOKEN);
	}

	@Test
	@DisplayName("만료된 리프레시 토큰은 AUTH_004로 거부한다")
	void expiredRefreshToken() {
		JwtProvider expiredProvider = provider(SECRET, Duration.ofMinutes(30), Duration.ofSeconds(-1));
		String token = expiredProvider.createRefreshToken(1L).token();

		assertErrorCode(() -> jwtProvider.parseRefreshToken(token), TokenErrorCode.INVALID_REFRESH_TOKEN);
	}

	private static JwtProvider provider(String secret, Duration access, Duration refresh) {
		return new JwtProvider(new JwtProperties(secret, access, refresh));
	}

	private static void assertErrorCode(Runnable action, TokenErrorCode expected) {
		assertThatThrownBy(action::run)
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(expected);
	}

}
