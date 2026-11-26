package com.mozip.domain.organization.entity;

import java.time.LocalDateTime;

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

// 운영진 개인 초대, 수락하는 회원의 연락처(휴대폰 또는 이메일)가 같아야 함
@Getter
@Entity
@Table(name = "invitations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invitation extends BaseEntity {

	public static final int VALID_DAYS = 7;

	// User.contactEmail(100자)과 같은 길이
	public static final int CONTACT_MAX_LENGTH = 100;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "organization_id", nullable = false)
	private Organization organization;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "inviter_id", nullable = false)
	private User inviter;

	// 초대 시점에 가입자면 바로 채움, 미가입자면 수락 시 채움
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "invitee_id")
	private User invitee;

	@Column(nullable = false, length = CONTACT_MAX_LENGTH)
	private String inviteeContact;

	// 합류 시 배정할 팀, 없으면 팀 미지정
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "team_id")
	private Team team;

	// 초대 수락 코드
	@Column(nullable = false, unique = true, length = 64)
	private String code;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private InvitationStatus status;

	@Column(nullable = false)
	private LocalDateTime expiresAt;

	private LocalDateTime respondedAt;

	private Invitation(Organization organization, User inviter, User invitee, String inviteeContact, Team team,
		String code, LocalDateTime expiresAt) {
		this.organization = organization;
		this.inviter = inviter;
		this.invitee = invitee;
		this.inviteeContact = inviteeContact;
		this.team = team;
		this.code = code;
		this.status = InvitationStatus.PENDING;
		this.expiresAt = expiresAt;
	}

	// 팀은 같은 단체의 것만 (팀 없는 단체 검사는 서비스에서)
	public static Invitation create(Organization organization, User inviter, User invitee, String inviteeContact,
		Team team, String code, LocalDateTime now) {
		if (team != null && !isSameOrganization(team.getOrganization(), organization)) {
			throw new IllegalArgumentException("초대하는 단체의 팀만 지정할 수 있습니다.");
		}
		return new Invitation(organization, inviter, invitee, inviteeContact, team, code,
			now.plusDays(VALID_DAYS));
	}

	public boolean isPending() {
		return status == InvitationStatus.PENDING;
	}

	public boolean isExpired(LocalDateTime now) {
		return !now.isBefore(expiresAt);
	}

	// 팀 미지정으로 되돌림 (팀 삭제 시 호출)
	public void clearTeam() {
		this.team = null;
	}

	// invitee를 채우고 수락 처리 (상태/만료 검사는 서비스에서)
	public void accept(User user, LocalDateTime now) {
		if (!isPending()) {
			throw new IllegalStateException("이미 처리된 초대입니다.");
		}
		this.invitee = user;
		this.status = InvitationStatus.ACCEPTED;
		this.respondedAt = now;
	}

	// 대기 중인 초대를 취소 (초대받은 회원이 다른 경로로 이미 합류했을 때)
	public void cancel(LocalDateTime now) {
		if (!isPending()) {
			throw new IllegalStateException("이미 처리된 초대입니다.");
		}
		this.status = InvitationStatus.CANCELED;
		this.respondedAt = now;
	}

	// 같은 단체인지 비교, 저장된 단체는 id로
	private static boolean isSameOrganization(Organization a, Organization b) {
		if (a == b) {
			return true;
		}
		return a.getId() != null && a.getId().equals(b.getId());
	}

}
