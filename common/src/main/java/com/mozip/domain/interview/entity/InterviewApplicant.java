package com.mozip.domain.interview.entity;

import com.mozip.domain.application.entity.Application;
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

// 면접에 배정된 지원자
@Getter
@Entity
@Table(name = "interview_applicants",
	uniqueConstraints = @UniqueConstraint(columnNames = {"interview_id", "application_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterviewApplicant extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "interview_id", nullable = false)
	private Interview interview;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "application_id", nullable = false)
	private Application application;

	private InterviewApplicant(Interview interview, Application application) {
		this.interview = interview;
		this.application = application;
	}

	public static InterviewApplicant create(Interview interview, Application application) {
		return new InterviewApplicant(interview, application);
	}

}
