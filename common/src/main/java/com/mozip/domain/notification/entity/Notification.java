package com.mozip.domain.notification.entity;

import com.mozip.domain.organization.entity.Organization;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "notifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private NotificationType type;

	// 알림에 표시할 단체 (지원자 알림도 지원한 단체를 표시), 단체와 무관한 알림이면 null
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "organization_id")
	private Organization organization;

	// TODO(B 결과 안내): message 도메인이 생기면 FK로 교체. 지금은 결과 안내 알림의 messageId만 들고 있음
	@Column(name = "message_id")
	private Long messageId;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(length = 500)
	private String linkUrl;

	@Column(nullable = false)
	private boolean isRead;

	private Notification(User user, NotificationType type, Organization organization, Long messageId, String title,
		String linkUrl) {
		this.user = user;
		this.type = type;
		this.organization = organization;
		this.messageId = messageId;
		this.title = title;
		this.linkUrl = linkUrl;
		this.isRead = false;
	}

	public static Notification create(User user, NotificationType type, Organization organization, Long messageId,
		String title, String linkUrl) {
		return new Notification(user, type, organization, messageId, title, linkUrl);
	}

	public void markRead() {
		this.isRead = true;
	}

}
