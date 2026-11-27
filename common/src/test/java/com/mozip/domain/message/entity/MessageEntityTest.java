package com.mozip.domain.message.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mozip.domain.user.entity.User;

// 메시지와 전달 내역의 불변 조건 검증 (DB 없이). 공고와 지원서는 여기서 보지 않아 null로 둠
class MessageEntityTest {

	private final User sender = User.create("01000000000", "encoded", "운영진", false, LocalDateTime.now());
	private final User otherSender = User.create("01000000001", "encoded", "다른 운영진", false, LocalDateTime.now());

	@Test
	@DisplayName("메시지는 DRAFT로 만들어지고 제목 100자, 본문 1000자까지 받는다")
	void createsDraftWithinLimits() {
		Message message = Message.create(null, sender, MessageType.DOC_RESULT, MessageTarget.PASS,
			"가".repeat(Message.SUBJECT_MAX_LENGTH), "나".repeat(Message.BODY_MAX_LENGTH));

		assertThat(message.getStatus()).isEqualTo(MessageStatus.DRAFT);
		assertThat(message.isDraft()).isTrue();
		assertThat(message.getSentAt()).isNull();
	}

	@Test
	@DisplayName("제목이 비었거나 100자를 넘으면 만들 수 없다")
	void rejectsInvalidSubject() {
		assertThatThrownBy(() -> create(" ", "본문")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> create(null, "본문")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> create("가".repeat(Message.SUBJECT_MAX_LENGTH + 1), "본문"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("본문이 비었거나 1000자를 넘으면 만들 수 없다")
	void rejectsInvalidBody() {
		assertThatThrownBy(() -> create("제목", "")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> create("제목", null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> create("제목", "나".repeat(Message.BODY_MAX_LENGTH + 1)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("DRAFT 메시지는 덮어쓸 수 있고 마지막으로 저장한 운영진이 발송자로 남는다")
	void updatesDraft() {
		Message message = create("제목", "본문");

		message.updateDraft(otherSender, "새 제목", "새 본문");

		assertThat(message.getSubject()).isEqualTo("새 제목");
		assertThat(message.getBody()).isEqualTo("새 본문");
		assertThat(message.getSender()).isEqualTo(otherSender);
		assertThatThrownBy(() -> message.updateDraft(sender, "제목", "나".repeat(Message.BODY_MAX_LENGTH + 1)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("발송을 시작한 메시지는 수정할 수 없다")
	void rejectsUpdateAfterSendingStarted() throws ReflectiveOperationException {
		Message sending = create("제목", "본문");
		setStatus(sending, MessageStatus.SENDING);
		Message sent = create("제목", "본문");
		setStatus(sent, MessageStatus.SENT);

		assertThatThrownBy(() -> sending.updateDraft(sender, "새 제목", "새 본문"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> sent.updateDraft(sender, "새 제목", "새 본문"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(sending.getSubject()).isEqualTo("제목");
	}

	@Test
	@DisplayName("전달 내역은 PENDING으로 만들어지고 주소가 비면 만들 수 없다")
	void createsPendingDelivery() {
		Message message = create("제목", "본문");

		MessageDelivery delivery = MessageDelivery.create(message, null, "applicant@gmail.com");

		assertThat(delivery.getStatus()).isEqualTo(MessageDeliveryStatus.PENDING);
		assertThat(delivery.getEmail()).isEqualTo("applicant@gmail.com");
		assertThat(delivery.getFailReason()).isNull();
		assertThatThrownBy(() -> MessageDelivery.create(message, null, " "))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> MessageDelivery.create(message, null, null))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private Message create(String subject, String body) {
		return Message.create(null, sender, MessageType.DOC_RESULT, MessageTarget.PASS, subject, body);
	}

	// 발송 시작은 리포지토리의 조건부 UPDATE로만 일어나 엔티티에 상태 변경 메서드가 없음. 테스트에서는 필드를 직접 바꿈
	private static void setStatus(Message message, MessageStatus status) throws ReflectiveOperationException {
		Field field = Message.class.getDeclaredField("status");
		field.setAccessible(true);
		field.set(message, status);
	}

}
