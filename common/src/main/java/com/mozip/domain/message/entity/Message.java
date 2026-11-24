package com.mozip.domain.message.entity;

import java.time.LocalDateTime;

import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.user.entity.User;
import com.mozip.global.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 지원자 안내 메시지. (공고, type, target)마다 하나라 합격자용과 불합격자용을 따로 쓰고 따로 발송함
@Getter
@Entity
@Table(name = "messages",
	uniqueConstraints = @UniqueConstraint(name = "uk_messages_posting_id_type_target",
		columnNames = {"posting_id", "type", "target"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Message extends BaseEntity {

	public static final int SUBJECT_MAX_LENGTH = 100;
	public static final int BODY_MAX_LENGTH = 1000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "posting_id", nullable = false)
	private Posting posting;

	// DRAFT면 마지막으로 저장한 운영진, 발송 뒤에는 발송을 누른 운영진
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sender_id", nullable = false)
	private User sender;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private MessageType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private MessageTarget target;

	@Column(nullable = false, length = SUBJECT_MAX_LENGTH)
	private String subject;

	// {이름} {포지션} 변수는 치환하지 않고 그대로 저장함. 치환은 발송 때 지원자별로
	@Column(nullable = false, length = BODY_MAX_LENGTH)
	private String body;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private MessageStatus status;

	// 전달이 전부 처리된 시각. SENT일 때만 값이 있음
	private LocalDateTime sentAt;

	private Message(Posting posting, User sender, MessageType type, MessageTarget target, String subject,
		String body) {
		this.posting = posting;
		this.sender = sender;
		this.type = type;
		this.target = target;
		this.subject = subject;
		this.body = body;
		this.status = MessageStatus.DRAFT;
	}

	public static Message create(Posting posting, User sender, MessageType type, MessageTarget target,
		String subject, String body) {
		validateContent(subject, body);
		return new Message(posting, sender, type, target, subject, body);
	}

	// 초안 덮어쓰기. 발송을 시작한 뒤에는 지원자가 받은 내용과 달라지면 안 되므로 DRAFT일 때만 가능
	public void updateDraft(User sender, String subject, String body) {
		if (!isDraft()) {
			throw new IllegalArgumentException("DRAFT가 아닌 메시지는 수정할 수 없습니다.");
		}
		validateContent(subject, body);
		this.sender = sender;
		this.subject = subject;
		this.body = body;
	}

	public boolean isDraft() {
		return status == MessageStatus.DRAFT;
	}

	private static void validateContent(String subject, String body) {
		if (subject == null || subject.isBlank() || subject.length() > SUBJECT_MAX_LENGTH) {
			throw new IllegalArgumentException("메시지 제목은 1~" + SUBJECT_MAX_LENGTH + "자여야 합니다.");
		}
		if (body == null || body.isBlank() || body.length() > BODY_MAX_LENGTH) {
			throw new IllegalArgumentException("메시지 본문은 1~" + BODY_MAX_LENGTH + "자여야 합니다.");
		}
	}

}
