package com.mozip.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.mozip.domain.auth.exception.RefreshTokenConsumedException;
import com.mozip.domain.auth.repository.RefreshTokenRepository;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;
import com.mozip.global.security.jwt.JwtProperties;
import com.mozip.global.security.jwt.JwtProvider;
import com.mozip.global.security.jwt.RefreshTokenClaims;
import com.mozip.global.security.jwt.TokenErrorCode;

class AuthServiceTest {

	private final UserRepository userRepository = mock(UserRepository.class);
	private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
	private final JwtProvider jwtProvider = mock(JwtProvider.class);
	private final AuthService authService = new AuthService(userRepository, refreshTokenRepository,
		mock(PasswordEncoder.class), jwtProvider, new JwtProperties("secret", Duration.ofMinutes(30), Duration.ofDays(14)));

	@Test
	@DisplayName("토큰을 소비한 뒤 회원 조회 DB가 실패하면 쿠키를 지우도록 소비 후 예외(500)로 감싼다")
	void databaseFailureAfterConsume() {
		when(jwtProvider.parseRefreshToken(anyString())).thenReturn(new RefreshTokenClaims(1L, "token-id"));
		when(refreshTokenRepository.consume("token-id")).thenReturn(Optional.of(1L));
		when(userRepository.findById(1L)).thenThrow(new DataAccessResourceFailureException("DB 연결 실패"));

		assertThatThrownBy(() -> authService.reissue("refresh-token"))
			.isInstanceOf(RefreshTokenConsumedException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR);
	}

	@Test
	@DisplayName("토큰을 소비하기 전 Redis가 실패하면 소비 후 예외로 감싸지 않는다")
	void storeFailureBeforeConsume() {
		when(jwtProvider.parseRefreshToken(anyString())).thenReturn(new RefreshTokenClaims(1L, "token-id"));
		when(refreshTokenRepository.consume("token-id"))
			.thenThrow(new BusinessException(TokenErrorCode.TOKEN_STORE_UNAVAILABLE));

		assertThatThrownBy(() -> authService.reissue("refresh-token"))
			.isInstanceOf(BusinessException.class)
			.isNotInstanceOf(RefreshTokenConsumedException.class);
	}

}
