package com.mozip.global.logging;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.mozip.global.security.AuthUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// 접근 로그에 넣을 userId를 요청에 적어 둠. SecurityContext는 AccessLogFilter로 돌아오기 전에 비워져서 여기서 옮김
@Component
public class AccessLogUserInterceptor implements HandlerInterceptor, WebMvcConfigurer {

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof AuthUser authUser) {
			request.setAttribute(AccessLogFilter.USER_ID_ATTRIBUTE, authUser.userId());
		}
		return true;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(this);
	}

}
