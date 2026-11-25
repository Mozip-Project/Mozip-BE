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

// 학교 인증 신청/이력. 메일 인증은 바로 APPROVED로 저장되고, 재학증명서는 PENDING으로 저장돼 관리자 심사를 기다림
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

	// 목록에 없는 학교를 직접 입력한 경우. 지금은 입력 경로가 없어 항상 null (재학증명서 인증은 schoolId만 받음)
	@Column(length = 100)
	private String schoolName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private SchoolVerificationMethod method;

	// SCHOOL_EMAIL일 때만 채움
	@Column(length = 255)
	private String email;

	// CERTIFICATE일 때만 채움
	@Column(length = 500)
	private String certificateUrl;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ReviewStatus status;

	@Column(length = 500)
	private String rejectReason;

	private LocalDateTime reviewedAt;

	private SchoolVerification(User user, School school, String schoolName, SchoolVerificationMethod method,
		String email, String certificateUrl, ReviewStatus status, LocalDateTime reviewedAt) {
		this.user = user;
		this.school = school;
		this.schoolName = schoolName;
		this.method = method;
		this.email = email;
		this.certificateUrl = certificateUrl;
		this.status = status;
		this.reviewedAt = reviewedAt;
	}

	// 학교 메일 인증번호가 맞으면 바로 승인 처리
	public static SchoolVerification createApprovedEmail(User user, School school, String email) {
		return new SchoolVerification(user, school, null, SchoolVerificationMethod.SCHOOL_EMAIL, email, null,
			ReviewStatus.APPROVED, LocalDateTime.now());
	}

	// 재학증명서는 관리자 심사 전까지 대기
	public static SchoolVerification createPendingCertificate(User user, School school, String certificateUrl) {
		return new SchoolVerification(user, school, null, SchoolVerificationMethod.CERTIFICATE, null, certificateUrl,
			ReviewStatus.PENDING, null);
	}

}
