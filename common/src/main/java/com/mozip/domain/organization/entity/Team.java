package com.mozip.domain.organization.entity;

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

@Getter
@Entity
@Table(name = "teams")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Team extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "organization_id", nullable = false)
	private Organization organization;

	@Column(nullable = false, length = 50)
	private String name;

	// 팀 설정 화면의 표시 순서 (0부터), saveTeams가 보낸 배열 순서를 그대로 저장
	@Column(nullable = false)
	private int sortOrder;

	private Team(Organization organization, String name, int sortOrder) {
		this.organization = organization;
		this.name = name;
		this.sortOrder = sortOrder;
	}

	public static Team create(Organization organization, String name, int sortOrder) {
		return new Team(organization, name, sortOrder);
	}

	// 팀 목록 전체 교체 시 기존 행은 id를 유지한 채 이름과 순서만 바꿈 (운영진 소속과 포지션 참조가 끊기지 않게)
	public void update(String name, int sortOrder) {
		this.name = name;
		this.sortOrder = sortOrder;
	}

}
