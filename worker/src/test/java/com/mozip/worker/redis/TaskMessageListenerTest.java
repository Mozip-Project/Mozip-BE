package com.mozip.worker.redis;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;

import com.mozip.global.redis.WorkerChannel;
import com.mozip.worker.mail.MailSender;

import tools.jackson.databind.ObjectMapper;

class TaskMessageListenerTest {

	private final MailSender mailSender = mock(MailSender.class);
	private final TaskMessageListener listener = new TaskMessageListener(mailSender, new ObjectMapper());

	@Test
	@DisplayName("type이 SEND_MAIL이면 to/subject/htmlBody/textBody를 MailSender로 넘긴다")
	void routesSendMailTaskToMailSender() {
		String payload = """
			{"type":"SEND_MAIL","to":"student@school.ac.kr","subject":"제목","htmlBody":"<p>본문</p>","textBody":"텍스트"}
			""";

		listener.onMessage(message(payload), null);

		verify(mailSender).send("student@school.ac.kr", "제목", "<p>본문</p>", "텍스트");
	}

	@Test
	@DisplayName("textBody가 없으면 null로 넘긴다")
	void passesNullWhenTextBodyMissing() {
		String payload = """
			{"type":"SEND_MAIL","to":"student@school.ac.kr","subject":"제목","htmlBody":"<p>본문</p>"}
			""";

		listener.onMessage(message(payload), null);

		verify(mailSender).send("student@school.ac.kr", "제목", "<p>본문</p>", null);
	}

	@Test
	@DisplayName("모르는 type이거나 JSON이 깨져 있어도 예외 없이 무시한다")
	void ignoresUnknownTypeOrMalformedPayload() {
		assertThatCode(() -> listener.onMessage(message("{\"type\":\"UNKNOWN\"}"), null))
			.doesNotThrowAnyException();
		assertThatCode(() -> listener.onMessage(message("not-json"), null)).doesNotThrowAnyException();

		verify(mailSender, never()).send(any(), any(), any(), any());
	}

	private DefaultMessage message(String payload) {
		return new DefaultMessage(WorkerChannel.TASKS.getBytes(), payload.getBytes());
	}

}
