package com.mozip.domain.notification.controller;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.mozip.domain.notification.sse.SseEmitterRegistry;
import com.mozip.global.security.AuthUser;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationSseController implements NotificationSseControllerApi {

	private final SseEmitterRegistry sseEmitterRegistry;

	// SSE는 text/event-stream 한 줄짜리 응답이 아니라 연결을 유지하며 이벤트를 흘려보내는 방식이라
	// 다른 REST처럼 ApiResponse로 감싸지 않음
	@Override
	@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter stream(@AuthenticationPrincipal AuthUser authUser) {
		return sseEmitterRegistry.register(authUser.userId());
	}

}
