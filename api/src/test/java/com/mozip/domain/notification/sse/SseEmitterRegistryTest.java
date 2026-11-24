package com.mozip.domain.notification.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseEmitterRegistryTest {

	private final SseEmitterRegistry registry = new SseEmitterRegistry();

	@Test
	@DisplayName("등록하면 그 회원의 연결 목록에 추가된다")
	void registerAddsToUserList() {
		SseEmitter emitter = registry.register(1L);

		assertThat(emitters(1L)).containsExactly(emitter);
	}

	@Test
	@DisplayName("완료된 연결에 push를 시도하면 전송이 실패해서 레지스트리에서 정리된다")
	void removesCompletedEmitterOnPush() {
		registry.register(1L);
		emitters(1L).get(0).complete();

		registry.push(1L, "{\"id\":1}");

		assertThat(emitters(1L)).isNull();
	}

	@Test
	@DisplayName("이 파드에 연결이 없는 회원에게 push해도 예외 없이 무시한다")
	void pushIgnoresUserWithoutLocalConnection() {
		assertThatCode(() -> registry.push(999L, "{\"id\":1}")).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("push하면 등록된 연결에만 전달하고 다른 회원 연결에는 보내지 않는다")
	void pushOnlyReachesTargetUser() {
		SseEmitter mine = registry.register(1L);
		SseEmitter other = registry.register(2L);

		assertThatCode(() -> registry.push(1L, "{\"id\":1}")).doesNotThrowAnyException();
		assertThat(emitters(1L)).containsExactly(mine);
		assertThat(emitters(2L)).containsExactly(other);
	}

	@Test
	@DisplayName("전송이 실패하면 재시도 없이 바로 연결을 정리한다")
	void removesConnectionOnFirstFailure() throws IOException {
		SseEmitter mockEmitter = mock(SseEmitter.class);
		doThrow(new IOException("전송 실패")).when(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));
		putEmitter(1L, mockEmitter);

		registry.push(1L, "{\"id\":1}");

		verify(mockEmitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
		assertThat(emitters(1L)).isNull();
	}

	@SuppressWarnings("unchecked")
	private List<SseEmitter> emitters(Long userId) {
		Map<Long, List<SseEmitter>> emittersByUserId =
			(Map<Long, List<SseEmitter>>)ReflectionTestUtils.getField(registry, "emittersByUserId");
		return emittersByUserId.get(userId);
	}

	@SuppressWarnings("unchecked")
	private void putEmitter(Long userId, SseEmitter emitter) {
		Map<Long, List<SseEmitter>> emittersByUserId =
			(Map<Long, List<SseEmitter>>)ReflectionTestUtils.getField(registry, "emittersByUserId");
		emittersByUserId.computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>()).add(emitter);
	}

}
