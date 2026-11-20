package com.mozip.domain.organization.entity;

import java.time.LocalDateTime;

import com.mozip.global.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

// 공용 초대 링크 (mozip.kr/invite/{token}). 누구나 토큰으로 합류할 수 있어 7일 뒤 만료
@Getter
@Entity
@Table(name = "invite_links")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InviteLink extends BaseEntity {

	public static final int VALID_DAYS = 7;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "organization_id", nullable = false)
	private Organization organization;

	@Column(nullable = false, unique = true, length = 64)
	private String token;

	@Column(nullable = false)
	private LocalDateTime expiresAt;

	private InviteLink(Organization organization, String token, LocalDateTime expiresAt) {
		this.organization = organization;
		this.token = token;
		this.expiresAt = expiresAt;
	}

	public static InviteLink create(Organization organization, String token, LocalDateTime now) {
		return new InviteLink(organization, token, now.plusDays(VALID_DAYS));
	}

	public boolean isExpired(LocalDateTime now) {
		return !now.isBefore(expiresAt);
	}

	// 운영진을 내보내면 그 사람이 알고 있는 링크로 다시 들어오지 못하게 바로 만료
	public void expire(LocalDateTime now) {
		if (expiresAt.isAfter(now)) {
			this.expiresAt = now;
		}
	}

}
