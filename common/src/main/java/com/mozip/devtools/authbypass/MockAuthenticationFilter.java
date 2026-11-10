package com.mozip.devtools.authbypass;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.mozip.global.common.response.ApiResponseWriter;
import com.mozip.global.exception.CommonErrorCode;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

// 인증 우회 중에만 등록, 토큰 없는 요청을 X-Mock-Role 헤더의 개발용 회원으로 인증
// 헤더가 없으면 auth.bypass.default-role, 잘못된 값이면 400 응답
// JwtAuthenticationFilter 뒤에 등록 (토큰이 있으면 토큰 우선)
@RequiredArgsConstructor
public class MockAuthenticationFilter extends OncePerRequestFilter {

	public static final String MOCK_ROLE_HEADER = "X-Mock-Role";

	private final MockUserRegistry mockUserRegistry;
	private final MockRole defaultRole;
	private final ApiResponseWriter apiResponseWriter;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
		FilterChain filterChain) throws ServletException, IOException {
		if (SecurityContextHolder.getContext().getAuthentication() == null) {
			Optional<MockRole> role = resolveRole(request.getHeader(MOCK_ROLE_HEADER));
			if (role.isEmpty()) {
				apiResponseWriter.writeError(response, CommonErrorCode.INVALID_INPUT,
					MOCK_ROLE_HEADER + " 헤더는 " + Arrays.toString(MockRole.values()) + " 중 하나여야 합니다.");
				return;
			}
			SecurityContextHolder.getContext().setAuthentication(mockUserRegistry.get(role.get()).toAuthentication());
		}
		filterChain.doFilter(request, response);
	}

	private Optional<MockRole> resolveRole(String header) {
		if (!StringUtils.hasText(header)) {
			return Optional.of(defaultRole);
		}
		try {
			return Optional.of(MockRole.valueOf(header.trim().toUpperCase(Locale.ROOT)));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

}
