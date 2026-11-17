package com.mozip.domain.application.entity;

import java.time.LocalDateTime;

import com.mozip.domain.posting.entity.Position;
import com.mozip.domain.posting.entity.Posting;
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

// 지원서
@Getter
@Entity
@Table(name = "applications",
	uniqueConstraints = @UniqueConstraint(columnNames = {"posting_id", "user_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Application extends BaseEntity {

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
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ApplicationStatus status;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private DocResult docResult;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private FinalResult finalResult;

	private LocalDateTime submittedAt;

	private Application(Posting posting, Position position, User user) {
		this.posting = posting;
		this.position = position;
		this.user = user;
		this.status = ApplicationStatus.DRAFT;
		this.docResult = DocResult.PENDING;
		this.finalResult = FinalResult.PENDING;
	}

	public static Application create(Posting posting, Position position, User user) {
		return new Application(posting, position, user);
	}

}
