package com.mozip.domain.notification.redis;

// api 파드 간 알림 발생 broadcast 채널. 모든 파드가 이 채널을 구독하고, 자기 로컬 SSE 연결에만 전달함
public final class NotificationChannel {

	public static final String EVENTS = "mozip:notification:events";

	private NotificationChannel() {
	}

}
