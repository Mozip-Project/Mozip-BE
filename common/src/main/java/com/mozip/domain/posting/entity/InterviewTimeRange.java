package com.mozip.domain.posting.entity;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

// 운영진이 연 면접 가능 시간대
@Getter
@Entity
@Table(name = "interview_time_ranges")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterviewTimeRange extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "posting_id", nullable = false)
	private Posting posting;

	// 이 시간을 쓰는 포지션(팀), null = 포지션 없는 모집 또는 전체 공통
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "position_id")
	private Position position;

	@Column(nullable = false)
	private LocalDate date;

	@Column(nullable = false)
	private LocalTime startTime;

	@Column(nullable = false)
	private LocalTime endTime;

	// 운영진이 끈 블록의 시작 시각, 지원자에게 안 보임
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(columnDefinition = "time[]")
	private List<LocalTime> closedSlots;

	private InterviewTimeRange(Posting posting, Position position, LocalDate date, LocalTime startTime,
		LocalTime endTime, List<LocalTime> closedSlots) {
		this.posting = posting;
		this.position = position;
		this.date = date;
		this.startTime = startTime;
		this.endTime = endTime;
		this.closedSlots = closedSlots;
	}

	public static InterviewTimeRange create(Posting posting, Position position, LocalDate date, LocalTime startTime,
		LocalTime endTime, List<LocalTime> closedSlots) {
		return new InterviewTimeRange(posting, position, date, startTime, endTime, closedSlots);
	}

}
