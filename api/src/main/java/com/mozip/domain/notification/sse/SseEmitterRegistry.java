package com.mozip.domain.notification.sse;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

// 이 파드가 들고 있는 SSE 연결만 관리. 다른 파드가 들고 있는 연결은 Redis 구독 쪽에서 각자 처리
@Slf4j
@Component
public class SseEmitterRegistry {

	private static final String EVENT_NAME = "notification";
	// access token 수명(30분)보다 짧게 잡아, 토큰이 만료/로그아웃된 뒤에도 연결이 살아남아 알림이 계속 나가는 걸 막음.
	// 타임아웃으로 끊기면 프론트 EventSource가 재연결하면서 인증을 다시 탐
	private static final long TIMEOUT_MS = Duration.ofMinutes(25).toMillis();

	private final Map<Long, List<SseEmitter>> emittersByUserId = new ConcurrentHashMap<>();

	public SseEmitter register(Long userId) {
		SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
		emittersByUserId.compute(userId, (key, emitters) -> {
			List<SseEmitter> target = emitters != null ? emitters : new CopyOnWriteArrayList<>();
			target.add(emitter);
			return target;
		});
		emitter.onCompletion(() -> remove(userId, emitter));
		emitter.onTimeout(() -> {
			remove(userId, emitter);
			emitter.complete();
		});
		emitter.onError(e -> remove(userId, emitter));
		// 등록만 하면 하트비트(최대 30초 뒤)까지 아무것도 안 보내 응답 헤더가 늦게 나감. 바로 한 줄 보내 헤더를 즉시 흘려보냄
		try {
			emitter.send(SseEmitter.event().comment("connected"));
		} catch (IOException | IllegalStateException e) {
			remove(userId, emitter);
		}
		return emitter;
	}

	// 이 파드에 해당 userId의 연결이 없으면(다른 파드가 들고 있거나 연결 안 함) 조용히 무시
	public void push(Long userId, String payloadJson) {
		List<SseEmitter> emitters = emittersByUserId.get(userId);
		if (emitters == null || emitters.isEmpty()) {
			return;
		}
		for (SseEmitter emitter : emitters) {
			if (!send(userId, emitter, payloadJson)) {
				remove(userId, emitter);
			}
		}
	}

	// 연결이 끊긴 뒤의 send는 재시도해도 같은 이유로 또 실패하므로, 복구는 프론트 EventSource의 재연결에 맡기고 바로 정리함
	private boolean send(Long userId, SseEmitter emitter, String payloadJson) {
		try {
			emitter.send(SseEmitter.event().name(EVENT_NAME).data(payloadJson));
			return true;
		} catch (IOException | IllegalStateException e) {
			log.debug("SSE 전송 실패로 연결 정리: userId={}, cause={}", userId, e.getClass().getSimpleName());
			return false;
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

	// 배포로 파드가 내려갈 때 열려 있는 연결을 정리. 프론트는 completion을 받고 재연결을 시도함
	@PreDestroy
	void closeAll() {
		emittersByUserId.values().forEach(emitters -> emitters.forEach(SseEmitter::complete));
	}

}
