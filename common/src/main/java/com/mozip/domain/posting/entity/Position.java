package com.mozip.domain.posting.entity;

import com.mozip.domain.organization.entity.Team;
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

// 공고별 모집 포지션
@Getter
@Entity
@Table(name = "positions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Position extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "posting_id", nullable = false)
	private Posting posting;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "team_id")
	private Team team;

	@Column(nullable = false, length = 50)
	private String name;

	// null = 모집 인원 미정
	private Integer headcount;

	@Column(nullable = false)
	private int sortOrder;

	private Position(Posting posting, Team team, String name, Integer headcount, int sortOrder) {
		this.posting = posting;
		this.team = team;
		this.name = name;
		this.headcount = headcount;
		this.sortOrder = sortOrder;
	}

	public static Position create(Posting posting, Team team, String name, Integer headcount, int sortOrder) {
		return new Position(posting, team, name, headcount, sortOrder);
	}

}
