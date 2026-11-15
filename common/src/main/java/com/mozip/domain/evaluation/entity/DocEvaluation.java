package com.mozip.domain.evaluation.entity;

import java.time.LocalDateTime;

import com.mozip.domain.application.entity.Application;
import com.mozip.domain.organization.entity.Member;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 운영진별 서류 평가
@Getter
@Entity
@Table(name = "doc_evaluations",
	uniqueConstraints = @UniqueConstraint(columnNames = {"application_id", "member_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocEvaluation extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "application_id", nullable = false)
	private Application application;

	// 평가한 운영진
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	// 자동 저장만 된 작성 중 상태에서는 null
	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private Opinion opinion;

	// null = 작성 중
	private LocalDateTime submittedAt;

	private DocEvaluation(Application application, Member member) {
		this.application = application;
		this.member = member;
	}

	public static DocEvaluation create(Application application, Member member) {
		return new DocEvaluation(application, member);
	}

}
