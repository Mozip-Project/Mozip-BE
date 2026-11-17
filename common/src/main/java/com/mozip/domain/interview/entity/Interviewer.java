package com.mozip.domain.interview.entity;

import com.mozip.domain.organization.entity.Member;
import com.mozip.global.common.BaseEntity;

import jakarta.persistence.Entity;
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

// 면접에 배정된 운영진
@Getter
@Entity
@Table(name = "interviewers",
	uniqueConstraints = @UniqueConstraint(columnNames = {"interview_id", "member_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Interviewer extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "interview_id", nullable = false)
	private Interview interview;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	private Interviewer(Interview interview, Member member) {
		this.interview = interview;
		this.member = member;
	}

	public static Interviewer create(Interview interview, Member member) {
		return new Interviewer(interview, member);
	}

}
