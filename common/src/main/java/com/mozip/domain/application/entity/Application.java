package com.mozip.domain.application.entity;

import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

	// 제출 시점 프로필 (JSON 문자열). GraphQL JSON 스칼라로 내보낼 때 파싱함
	@Column(columnDefinition = "TEXT")
	private String profileSnapshot;

	// 면접 가능 시간 블록의 시작 시각 목록
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(columnDefinition = "timestamp[]")
	private List<LocalDateTime> availableTimes;

	private Application(Posting posting, Position position, User user) {
		this.posting = posting;
		this.position = position;
		this.user = user;
		this.status = ApplicationStatus.DRAFT;
		this.docResult = DocResult.PENDING;
		this.finalResult = FinalResult.PENDING;
		this.availableTimes = List.of();
	}

	public static Application create(Posting posting, Position position, User user) {
		return new Application(posting, position, user);
	}

	// DRAFT 재저장. 포지션과 면접 가능 시간을 통째로 덮어씀 (답변 목록 자체는 리포지토리에서 교체)
	public void updateDraft(Position position, List<LocalDateTime> availableTimes) {
		this.position = position;
		this.availableTimes = availableTimes == null ? List.of() : availableTimes;
	}

	// 면접 가능 시간만 통째로 교체. 제출 뒤 재선택에서도 써서 상태를 가리지 않음
	public void changeAvailableTimes(List<LocalDateTime> availableTimes) {
		this.availableTimes = availableTimes == null ? List.of() : availableTimes;
	}

	public void submit(String profileSnapshot, LocalDateTime now) {
		this.status = ApplicationStatus.SUBMITTED;
		this.submittedAt = now;
		this.profileSnapshot = profileSnapshot;
	}

	public boolean isDraft() {
		return status == ApplicationStatus.DRAFT;
	}

	public boolean isOwnedBy(Long userId) {
		return user.getId().equals(userId);
	}

}
