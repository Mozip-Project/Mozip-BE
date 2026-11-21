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

// 단체 운영진 (회원의 단체 소속)
// 소속 unique는 소속 중인 행(left_at is null)에만 걸리고 DDL에만 있음
@Getter
@Entity
@Table(name = "members")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "organization_id", nullable = false)
	private Organization organization;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	// 한 팀에만 소속, 팀 없는 단체면 null
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "team_id")
	private Team team;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private MemberRole role;

	// 내보내진 시각, 소속 중이면 null
	private LocalDateTime leftAt;

	private Member(Organization organization, User user, Team team, MemberRole role) {
		this.organization = organization;
		this.user = user;
		this.team = team;
		this.role = role;
	}

	// 팀 없는 단체에 팀 지정, 다른 단체의 팀 지정은 거부
	public static Member create(Organization organization, User user, Team team, MemberRole role) {
		if (team != null) {
			if (organization.isNoTeam()) {
				throw new IllegalArgumentException("팀 없는 단체의 운영진에는 팀을 지정할 수 없습니다.");
			}
			if (!isSameOrganization(team.getOrganization(), organization)) {
				throw new IllegalArgumentException("운영진과 같은 단체의 팀만 지정할 수 있습니다.");
			}
		}
		return new Member(organization, user, team, role);
	}

	public boolean isRepresentative() {
		return role == MemberRole.REPRESENTATIVE;
	}

	// 내보내기 (soft delete), 이미 나간 운영진이면 예외
	public void leave(LocalDateTime now) {
		if (leftAt != null) {
			throw new IllegalStateException("이미 단체를 나간 운영진입니다.");
		}
		this.leftAt = now;
	}

	// 프록시와 실제 객체가 섞일 수 있어 저장된 경우 id로 비교
	private static boolean isSameOrganization(Organization a, Organization b) {
		if (a == b) {
			return true;
		}
		return a.getId() != null && a.getId().equals(b.getId());
	}

}
