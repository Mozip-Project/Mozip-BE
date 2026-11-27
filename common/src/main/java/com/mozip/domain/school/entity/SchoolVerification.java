package com.mozip.domain.school.entity;

import java.time.LocalDateTime;

import com.mozip.domain.user.entity.User;
import com.mozip.global.common.BaseEntity;
import com.mozip.global.common.ReviewStatus;

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

// 학교 인증 이력. 메일 인증은 바로 APPROVED로 저장됨
@Getter
@Entity
@Table(name = "school_verifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchoolVerification extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "school_id")
	private School school;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private SchoolVerificationMethod method;

	@Column(length = 255)
	private String email;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ReviewStatus status;

	@Column(length = 500)
	private String rejectReason;

	private LocalDateTime reviewedAt;

	private SchoolVerification(User user, School school, SchoolVerificationMethod method, String email,
		ReviewStatus status, LocalDateTime reviewedAt) {
		this.user = user;
		this.school = school;
		this.method = method;
		this.email = email;
		this.status = status;
		this.reviewedAt = reviewedAt;
	}

	// 학교 메일 인증번호가 맞으면 바로 승인 처리
	public static SchoolVerification createApprovedEmail(User user, School school, String email) {
		return new SchoolVerification(user, school, SchoolVerificationMethod.SCHOOL_EMAIL, email,
			ReviewStatus.APPROVED, LocalDateTime.now());
	}

}
