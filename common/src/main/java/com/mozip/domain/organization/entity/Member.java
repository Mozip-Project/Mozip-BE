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
// 같은 회원의 소속 unique는 소속 중인 행(left_at is null)에만 걸리는 부분 unique index라 DDL에만 있음
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

	// 내보내진 시각, 소속 중이면 null. 평가 기록이 운영진을 참조해 행은 지우지 않고 이 값으로 소속 여부를 가름
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

	// 내보내기 (soft delete). 이미 나간 운영진을 다시 내보내는 건 호출 쪽 조회 누락이라 예외
	public void leave(LocalDateTime now) {
		if (leftAt != null) {
			throw new IllegalStateException("이미 단체를 나간 운영진입니다.");
		}
		this.leftAt = now;
	}

	// 대표자 인수 수락 시 두 사람의 역할을 바꿈. 대표자 unique index 때문에 기존 대표자를 먼저 내리고 flush해야 함
	public void changeRole(MemberRole role) {
		if (leftAt != null) {
			throw new IllegalStateException("단체를 나간 운영진의 역할은 바꿀 수 없습니다.");
		}
		this.role = role;
	}

	// 소속 팀이 삭제되면 팀 미지정으로 둠 (팀 목록 저장에서 호출)
	public void leaveTeam() {
		this.team = null;
	}

	// 프록시와 실제 객체가 섞일 수 있어 저장된 경우 id로 비교
	private static boolean isSameOrganization(Organization a, Organization b) {
		if (a == b) {
			return true;
		}
		return a.getId() != null && a.getId().equals(b.getId());
	}

}
