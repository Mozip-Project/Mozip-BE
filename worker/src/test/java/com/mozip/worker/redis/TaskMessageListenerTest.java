package com.mozip.worker.redis;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;

import com.mozip.domain.school.repository.SchoolEmailVerificationCodeRepository;
import com.mozip.global.redis.WorkerChannel;
import com.mozip.worker.mail.MailSender;

import tools.jackson.databind.ObjectMapper;

class TaskMessageListenerTest {

	private final MailSender mailSender = mock(MailSender.class);
	private final SchoolEmailVerificationCodeRepository codeRepository = mock(SchoolEmailVerificationCodeRepository.class);
	private final TaskMessageListener listener = new TaskMessageListener(mailSender, codeRepository, new ObjectMapper());

	@Test
	@DisplayName("type이 SEND_MAIL이면 무시한다 (MailTaskStream으로 옮겨서 이 리스너가 처리하지 않음)")
	void ignoresSendMailTaskSinceItMovedToStream() {
		String payload = """
			{"type":"SEND_MAIL","to":"student@school.ac.kr","subject":"제목","body":"본문"}
			""";

		listener.onMessage(message(payload), null);

		verify(mailSender, never()).send(any(), any(), any());
	}

	@Test
	@DisplayName("type이 SCHOOL_VERIFICATION_MAIL이고 발송에 성공하면 markSent를 남긴다")
	void marksSentWhenSchoolVerificationMailSucceeds() {
		String payload = """
			{"type":"SCHOOL_VERIFICATION_MAIL","to":"student@school.ac.kr","subject":"제목","body":"본문"}
			""";

		listener.onMessage(message(payload), null);

		verify(mailSender).send("student@school.ac.kr", "제목", "본문");
		verify(codeRepository).markSent("student@school.ac.kr");
	}

	@Test
	@DisplayName("type이 SCHOOL_VERIFICATION_MAIL이고 발송에 실패하면 markFailed를 남기고 인증번호를 지운다")
	void marksFailedAndDeletesCodeWhenSchoolVerificationMailFails() {
		doThrow(new RuntimeException("SES 실패")).when(mailSender).send(any(), any(), any());
		String payload = """
			{"type":"SCHOOL_VERIFICATION_MAIL","to":"student@school.ac.kr","subject":"제목","body":"본문"}
			""";

		assertThatCode(() -> listener.onMessage(message(payload), null)).doesNotThrowAnyException();

		verify(codeRepository).markFailed("student@school.ac.kr");
		verify(codeRepository).deleteCode("student@school.ac.kr");
	}

	@Test
	@DisplayName("모르는 type이거나 JSON이 깨져 있어도 예외 없이 무시한다")
	void ignoresUnknownTypeOrMalformedPayload() {
		assertThatCode(() -> listener.onMessage(message("{\"type\":\"UNKNOWN\"}"), null))
			.doesNotThrowAnyException();
		assertThatCode(() -> listener.onMessage(message("not-json"), null)).doesNotThrowAnyException();

		verify(mailSender, never()).send(any(), any(), any());
	}

	private DefaultMessage message(String payload) {
		return new DefaultMessage(WorkerChannel.TASKS.getBytes(), payload.getBytes());
	}

}
