package com.mozip.domain.posting.entity;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 공고 신고. 신고 처리(검토, 조치)는 관리자 화면 몫이라 여기서는 접수만 함
@Getter
@Entity
@Table(name = "reports",
	uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "posting_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "posting_id", nullable = false)
	private Posting posting;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ReportReason reason;

	@Column(columnDefinition = "TEXT")
	private String detail;

	private Report(User user, Posting posting, ReportReason reason, String detail) {
		this.user = user;
		this.posting = posting;
		this.reason = reason;
		this.detail = detail;
	}

	public static Report create(User user, Posting posting, ReportReason reason, String detail) {
		return new Report(user, posting, reason, detail);
	}

}
