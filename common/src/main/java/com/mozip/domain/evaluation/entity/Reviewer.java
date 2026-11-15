package com.mozip.domain.evaluation.entity;

import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.posting.entity.Position;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.global.common.BaseEntity;

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

// 서류 평가 담당 운영진 (공고 포지션별 배정)
@Getter
@Entity
@Table(name = "reviewers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reviewer extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "posting_id", nullable = false)
	private Posting posting;

	// 포지션 없는 모집이면 null
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "position_id")
	private Position position;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	private Reviewer(Posting posting, Position position, Member member) {
		this.posting = posting;
		this.position = position;
		this.member = member;
	}

	public static Reviewer create(Posting posting, Position position, Member member) {
		return new Reviewer(posting, position, member);
	}

}
