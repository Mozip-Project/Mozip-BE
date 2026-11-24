package com.mozip.domain.message.entity;

import com.mozip.domain.application.entity.Application;
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

// 지원자별 메일 전달 내역. 발송 시작 때 이메일 경고가 없는 받는 사람마다 PENDING으로 만들어짐
@Getter
@Entity
@Table(name = "message_deliveries",
	uniqueConstraints = @UniqueConstraint(name = "uk_message_deliveries_message_id_application_id",
		columnNames = {"message_id", "application_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageDelivery extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "message_id", nullable = false)
	private Message message;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "application_id", nullable = false)
	private Application application;

	// 발송 시작 시점의 연락 이메일. 이후 회원이 주소를 바꿔도 실제로 보낸 주소가 남음
	@Column(nullable = false, length = 100)
	private String email;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private MessageDeliveryStatus status;

	// FAILED일 때 화면에 보여줄 사유
	@Column(length = 255)
	private String failReason;

	private MessageDelivery(Message message, Application application, String email) {
		this.message = message;
		this.application = application;
		this.email = email;
		this.status = MessageDeliveryStatus.PENDING;
	}

	public static MessageDelivery create(Message message, Application application, String email) {
		if (email == null || email.isBlank()) {
			throw new IllegalArgumentException("보낼 주소가 없는 전달 내역은 만들 수 없습니다.");
		}
		return new MessageDelivery(message, application, email);
	}

}
