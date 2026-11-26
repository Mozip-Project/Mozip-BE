package com.mozip.worker.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.mozip.global.redis.MailTaskStream;
import com.mozip.worker.mail.MailSender;

class MailTaskProcessorTest {

	private final MailSender mailSender = mock(MailSender.class);
	private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
	@SuppressWarnings("unchecked")
	private final StreamOperations<String, String, String> streamOperations = mock(StreamOperations.class);
	private final MailTaskProcessor processor = new MailTaskProcessor(mailSender, redisTemplate);

	private final MapRecord<String, String, String> record = MapRecord.create(MailTaskStream.KEY,
		Map.of("to", "a@b.com", "subject", "제목", "body", "본문"));

	@Test
	@DisplayName("발송에 성공하면 ACK하고 true를 반환한다")
	void acknowledgesAndReturnsTrueOnSuccess() {
		when(redisTemplate.<String, String>opsForStream()).thenReturn(streamOperations);

		boolean result = processor.process(record);

		assertThat(result).isTrue();
		verify(mailSender).send("a@b.com", "제목", "본문");
		verify(streamOperations).acknowledge(eq(MailTaskStream.CONSUMER_GROUP), eq(record));
	}

	@Test
	@DisplayName("발송에 실패하면 ACK하지 않고 false를 반환한다")
	void doesNotAcknowledgeAndReturnsFalseOnFailure() {
		doThrow(new RuntimeException("SES 실패")).when(mailSender).send(anyString(), anyString(), anyString());

		boolean result = processor.process(record);

		assertThat(result).isFalse();
		verify(streamOperations, never()).acknowledge(any(), any(MapRecord.class));
	}

}
