package com.mozip.global.security.jwt;

import java.io.IOException;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.mozip.global.exception.BusinessException;
import com.mozip.global.security.AuthUser;
import com.mozip.global.security.cookie.AuthCookieManager;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

// access_token 쿠키를 검증해 SecurityContext에 인증 정보 저장 (REST, GraphQL 공통)
// 토큰이 없거나 잘못돼도 통과, 인증이 필요한 요청이면 SecurityErrorHandler가 401 응답
// SecurityConfig에서 직접 생성 (@Component로 두면 서블릿 필터로 중복 등록됨)
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	// 검증 실패 사유(AUTH_002, AUTH_003)를 저장해 401 응답에 사용
	public static final String AUTH_ERROR_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".AUTH_ERROR";

	private final JwtProvider jwtProvider;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
		FilterChain filterChain) throws ServletException, IOException {
		AuthCookieManager.readCookie(request, AuthCookieManager.ACCESS_TOKEN_COOKIE)
			.ifPresent(token -> authenticate(request, token));
		filterChain.doFilter(request, response);
	}

	private void authenticate(HttpServletRequest request, String token) {
		try {
			AccessTokenClaims claims = jwtProvider.parseAccessToken(token);
			AuthUser authUser = new AuthUser(claims.userId(), claims.authority());
			SecurityContextHolder.getContext().setAuthentication(authUser.toAuthentication());
		} catch (BusinessException e) {
			request.setAttribute(AUTH_ERROR_ATTRIBUTE, e.getErrorCode());
		}
	}

}
