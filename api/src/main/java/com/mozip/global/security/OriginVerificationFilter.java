package com.mozip.global.security;

import java.io.IOException;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import com.mozip.global.common.response.ApiResponseWriter;
import com.mozip.global.exception.CommonErrorCode;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

// 상태를 바꾸는 요청(POST 등)의 출처가 허용 목록에 없으면 403으로 거부 (CSRF 방어)
// 1차 방어는 SameSite 쿠키와 CORS이며, 이 필터는 그 설정이 바뀌어도 남는 2차 방어
// 출처는 Origin 헤더, 없으면 Referer 헤더로 판단하고 둘 다 없는 요청(브라우저 외 클라이언트)은 통과
@Slf4j
public class OriginVerificationFilter extends OncePerRequestFilter {

	private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

	private final Set<String> allowedOrigins;
	private final ApiResponseWriter apiResponseWriter;

	public OriginVerificationFilter(Collection<String> allowedOrigins, ApiResponseWriter apiResponseWriter) {
		this.allowedOrigins = allowedOrigins.stream()
			.map(OriginVerificationFilter::normalize)
			.collect(Collectors.toUnmodifiableSet());
		this.apiResponseWriter = apiResponseWriter;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
		FilterChain filterChain) throws ServletException, IOException {
		if (SAFE_METHODS.contains(request.getMethod()) || isAllowed(request)) {
			filterChain.doFilter(request, response);
			return;
		}
		apiResponseWriter.writeError(response, CommonErrorCode.FORBIDDEN);
	}

	private boolean isAllowed(HttpServletRequest request) {
		String source = request.getHeader(HttpHeaders.ORIGIN);
		if (source == null) {
			source = request.getHeader(HttpHeaders.REFERER);
		}
		if (source == null) {
			return true;
		}
		String origin;
		try {
			origin = normalize(source);
		} catch (IllegalArgumentException e) {
			log.warn("출처 헤더 형식 오류로 요청 거부: {} {}", request.getMethod(), request.getRequestURI());
			return false;
		}
		// 백엔드 자신의 주소에서 온 요청(Swagger UI, GraphiQL)은 허용
		boolean allowed = origin.equals(requestOrigin(request)) || allowedOrigins.contains(origin);
		if (!allowed) {
			log.warn("허용되지 않은 출처의 요청 거부: origin={}, {} {}", origin, request.getMethod(), request.getRequestURI());
		}
		return allowed;
	}

	private static String requestOrigin(HttpServletRequest request) {
		return origin(request.getScheme(), request.getServerName(), request.getServerPort());
	}

	// Origin 값이나 Referer URL에서 scheme://host[:port]만 남김 (기본 포트는 생략, 대소문자 통일)
	private static String normalize(String url) {
		UriComponents components = UriComponentsBuilder.fromUriString(url.trim()).build();
		if (components.getScheme() == null || components.getHost() == null) {
			throw new IllegalArgumentException("scheme 또는 host가 없는 출처: " + url);
		}
		return origin(components.getScheme(), components.getHost(), components.getPort());
	}

	private static String origin(String scheme, String host, int port) {
		String lowerScheme = scheme.toLowerCase(Locale.ROOT);
		int defaultPort = "https".equals(lowerScheme) ? 443 : 80;
		String portPart = port == -1 || port == defaultPort ? "" : ":" + port;
		return lowerScheme + "://" + host.toLowerCase(Locale.ROOT) + portPart;
	}

}
