package com.mozip.domain.notification.sse;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.extern.slf4j.Slf4j;

// 이 파드가 들고 있는 SSE 연결만 관리. 다른 파드가 들고 있는 연결은 Redis 구독 쪽에서 각자 처리
@Slf4j
@Component
public class SseEmitterRegistry {

	private static final String EVENT_NAME = "notification";
	// IOException은 순간적인 버퍼/네트워크 문제일 수 있어 재시도. IllegalStateException(연결 이미 끝남)은 재시도해도
	// 항상 똑같이 실패하므로 즉시 정리함
	private static final int MAX_SEND_ATTEMPTS = 3;
	private static final long RETRY_DELAY_MS = 100;

	private final Map<Long, List<SseEmitter>> emittersByUserId = new ConcurrentHashMap<>();

	public SseEmitter register(Long userId) {
		SseEmitter emitter = new SseEmitter(0L);
		List<SseEmitter> emitters = emittersByUserId.computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>());
		emitters.add(emitter);
		emitter.onCompletion(() -> remove(userId, emitter));
		emitter.onTimeout(() -> remove(userId, emitter));
		emitter.onError(e -> remove(userId, emitter));
		return emitter;
	}

	// 이 파드에 해당 userId의 연결이 없으면(다른 파드가 들고 있거나 연결 안 함) 조용히 무시
	public void push(Long userId, String payloadJson) {
		List<SseEmitter> emitters = emittersByUserId.get(userId);
		if (emitters == null || emitters.isEmpty()) {
			return;
		}
		for (SseEmitter emitter : emitters) {
			if (!sendWithRetry(userId, emitter, payloadJson)) {
				remove(userId, emitter);
			}
		}
	}

	private boolean sendWithRetry(Long userId, SseEmitter emitter, String payloadJson) {
		for (int attempt = 1; attempt <= MAX_SEND_ATTEMPTS; attempt++) {
			try {
				emitter.send(SseEmitter.event().name(EVENT_NAME).data(payloadJson));
				return true;
			} catch (IllegalStateException e) {
				log.warn("SSE 연결이 이미 끝나 있어 정리함: userId={}, cause={}", userId, e.getClass().getSimpleName());
				return false;
			} catch (IOException e) {
				log.warn("SSE 전송 실패 ({}/{}번째 시도): userId={}, cause={}", attempt, MAX_SEND_ATTEMPTS, userId,
					e.getClass().getSimpleName());
				if (attempt == MAX_SEND_ATTEMPTS) {
					return false;
				}
				sleep(RETRY_DELAY_MS);
			}
		}
		return false;
	}

	private void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	// Traefik(reverse proxy)이나 중간 네트워크 구간의 idle timeout으로 연결이 끊기지 않도록 30초마다 빈 주석 라인 전송
	@Scheduled(fixedRate = 30_000)
	public void heartbeat() {
		emittersByUserId.forEach((userId, emitters) -> {
			for (SseEmitter emitter : List.copyOf(emitters)) {
				try {
					emitter.send(SseEmitter.event().comment("ping"));
				} catch (IOException | IllegalStateException e) {
					remove(userId, emitter);
				}
			}
		});
	}

	// "리스트에서 제거"와 "비면 키 삭제"를 한 번의 computeIfPresent로 묶어야 함. 따로 하면 그 사이에
	// register()의 computeIfAbsent가 끼어들어, 막 추가된 새 연결이 든 리스트를 통째로 지워버릴 수 있음
	// (키가 같으면 computeIfAbsent/computeIfPresent는 서로 원자적으로 배타적임)
	private void remove(Long userId, SseEmitter emitter) {
		emittersByUserId.computeIfPresent(userId, (key, emitters) -> {
			emitters.remove(emitter);
			return emitters.isEmpty() ? null : emitters;
		});
	}

}
