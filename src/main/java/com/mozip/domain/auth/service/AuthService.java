package com.mozip.domain.auth.service;

import java.time.LocalDateTime;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.domain.auth.dto.LoginRequest;
import com.mozip.domain.auth.dto.LoginResponse;
import com.mozip.domain.auth.dto.SignupRequest;
import com.mozip.domain.auth.dto.SignupResponse;
import com.mozip.domain.auth.exception.AuthErrorCode;
import com.mozip.domain.auth.repository.RefreshTokenRepository;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.exception.UserErrorCode;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.security.jwt.IssuedRefreshToken;
import com.mozip.global.security.jwt.JwtProperties;
import com.mozip.global.security.jwt.JwtProvider;
import com.mozip.global.security.jwt.RefreshTokenClaims;
import com.mozip.global.security.jwt.TokenErrorCode;

import lombok.extern.slf4j.Slf4j;

// 로그인, 재발급, 로그아웃은 트랜잭션 없이 실행
// BCrypt 비교(수십~수백 ms) 동안 DB 커넥션을 붙잡지 않도록 하며, JPA 쓰기가 있는 가입만 @Transactional
@Slf4j
@Service
public class AuthService {

	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtProvider jwtProvider;
	private final JwtProperties jwtProperties;

	// 없는 번호로 로그인해도 비밀번호 비교 수행 (응답 시간으로 가입 여부 추측 방지)
	private final String dummyPasswordHash;

	public AuthService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
		PasswordEncoder passwordEncoder, JwtProvider jwtProvider, JwtProperties jwtProperties) {
		this.userRepository = userRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtProvider = jwtProvider;
		this.jwtProperties = jwtProperties;
		this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
	}

	@Transactional
	public SignupResponse signup(SignupRequest request) {
		if (userRepository.existsByPhone(request.phone())) {
			throw new BusinessException(UserErrorCode.DUPLICATE_PHONE);
		}
		User user = User.create(request.phone(), passwordEncoder.encode(request.password()), request.name(),
			request.marketingAgreed(), LocalDateTime.now());
		try {
			// 동시 가입으로 unique 제약 위반 시 DUPLICATE_PHONE 응답
			userRepository.saveAndFlush(user);
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(UserErrorCode.DUPLICATE_PHONE);
		}
		return new SignupResponse(user.getId());
	}

	public LoginResult login(LoginRequest request) {
		User user = userRepository.findByPhone(request.phone()).orElse(null);
		String passwordHash = user != null ? user.getPassword() : dummyPasswordHash;
		boolean matches = passwordEncoder.matches(request.password(), passwordHash);
		if (user == null || !matches) {
			throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
		}
		if (!user.isActive()) {
			throw new BusinessException(AuthErrorCode.INACTIVE_USER).addContext("userId", user.getId());
		}
		return new LoginResult(issueTokens(user), LoginResponse.from(user));
	}

	// 사용한 리프레시 토큰은 폐기 후 새로 발급 (RTR)
	public AuthTokens reissue(String refreshToken) {
		if (refreshToken == null) {
			throw new BusinessException(TokenErrorCode.INVALID_REFRESH_TOKEN);
		}
		RefreshTokenClaims claims = jwtProvider.parseRefreshToken(refreshToken);
		Long storedUserId = refreshTokenRepository.consume(claims.tokenId())
			.orElseThrow(() -> revokeAllSessions(claims));
		if (!storedUserId.equals(claims.userId())) {
			throw new BusinessException(TokenErrorCode.INVALID_REFRESH_TOKEN).addContext("userId", claims.userId());
		}
		User user = userRepository.findById(claims.userId())
			.orElseThrow(() -> new BusinessException(TokenErrorCode.INVALID_REFRESH_TOKEN));
		if (!user.isActive()) {
			throw new BusinessException(AuthErrorCode.INACTIVE_USER).addContext("userId", user.getId());
		}
		return issueTokens(user);
	}

	// 서명은 유효한데 저장소에 없는 토큰은 이미 사용됐거나 폐기된 토큰이 재사용된 것
	// 탈취로 보고 그 회원의 모든 기기 세션을 끊어 먼저 재발급받은 쪽도 다시 로그인하게 함
	private BusinessException revokeAllSessions(RefreshTokenClaims claims) {
		refreshTokenRepository.deleteAllByUserId(claims.userId());
		log.warn("리프레시 토큰 재사용 감지로 전체 세션 폐기: userId={}", claims.userId());
		return new BusinessException(TokenErrorCode.INVALID_REFRESH_TOKEN)
			.addContext("userId", claims.userId())
			.addContext("reason", "reused");
	}

	// 토큰이 없거나 잘못돼도 로그아웃은 성공 처리
	public void logout(String refreshToken) {
		if (refreshToken == null) {
			return;
		}
		try {
			refreshTokenRepository.delete(jwtProvider.parseRefreshToken(refreshToken).tokenId());
		} catch (BusinessException ignored) {
			// 쿠키 삭제만 진행
		}
	}

	private AuthTokens issueTokens(User user) {
		String accessToken = jwtProvider.createAccessToken(user.getId(), user.getAuthority());
		IssuedRefreshToken refreshToken = jwtProvider.createRefreshToken(user.getId());
		refreshTokenRepository.save(refreshToken.tokenId(), user.getId(), jwtProperties.refreshTokenExpiration());
		return new AuthTokens(accessToken, refreshToken.token());
	}

}
