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

// 운영진 개인 초대. 연락처(휴대폰 또는 이메일)로 보내고 수락하는 회원의 연락처가 같아야 함
@Getter
@Entity
@Table(name = "invitations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invitation extends BaseEntity {

	public static final int VALID_DAYS = 7;

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

	@Column(nullable = false, length = 255)
	private String inviteeContact;

	// 합류 시 배정할 팀, 없으면 팀 미지정
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "team_id")
	private Team team;

	// 초대 수락에 쓰는 코드, 알림 링크에 담김
	@Column(nullable = false, unique = true, length = 64)
	private String code;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
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

	// 팀은 같은 단체의 것만. 팀 없는 단체(noTeam) 검사는 서비스에서 (단체 설정은 엔티티 밖 정보)
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

	// 지정한 팀이 삭제되면 팀 없이 합류하게 둠 (팀 목록 저장에서 호출)
	public void clearTeam() {
		this.team = null;
	}

	// 미가입자 초대는 여기서 invitee가 채워짐. 상태/만료 검사는 서비스가 에러 코드별로 먼저 하고 들어옴
	public void accept(User user, LocalDateTime now) {
		if (!isPending()) {
			throw new IllegalStateException("이미 처리된 초대입니다.");
		}
		this.invitee = user;
		this.status = InvitationStatus.ACCEPTED;
		this.respondedAt = now;
	}

	// 프록시와 실제 객체가 섞일 수 있어 저장된 경우 id로 비교 (Member와 같은 규칙)
	private static boolean isSameOrganization(Organization a, Organization b) {
		if (a == b) {
			return true;
		}
		return a.getId() != null && a.getId().equals(b.getId());
	}

}
