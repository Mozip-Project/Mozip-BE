package com.mozip.domain.interview.entity;

import java.time.LocalDateTime;

import com.mozip.domain.posting.entity.Posting;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 공고별 타임테이블 상태. 면접실 열 개수와 확정 후 수정 모드 여부
@Getter
@Entity
@Table(name = "interview_timetable_states",
	uniqueConstraints = @UniqueConstraint(name = "uk_interview_timetable_states_posting", columnNames = "posting_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterviewTimetableState extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "posting_id", nullable = false)
	private Posting posting;

	// 운영진이 [면접 추가]로 늘린 것까지 포함한 면접실 열 개수
	@Column(nullable = false)
	private int trackCount;

	// 확정 후 수정 모드에 들어간 시각, 수정 모드가 아니면 null
	private LocalDateTime editingStartedAt;

	// 수정 모드에 들어갈 때의 열 개수, 수정 취소하면 이 값으로 되돌림
	private Integer snapshotTrackCount;

	private InterviewTimetableState(Posting posting, int trackCount) {
		this.posting = posting;
		this.trackCount = trackCount;
	}

	public static InterviewTimetableState create(Posting posting, int trackCount) {
		return new InterviewTimetableState(posting, trackCount);
	}

	public boolean isEditing() {
		return editingStartedAt != null;
	}

	public void changeTrackCount(int trackCount) {
		this.trackCount = trackCount;
	}

	public void startEditing(LocalDateTime now) {
		this.editingStartedAt = now;
		this.snapshotTrackCount = trackCount;
	}

	// 수정한 내용을 그대로 두고 수정 모드만 끝냄
	public void finishEditing() {
		this.editingStartedAt = null;
		this.snapshotTrackCount = null;
	}

	// 열 개수를 수정 모드 전으로 되돌리고 수정 모드를 끝냄
	public void cancelEditing() {
		if (snapshotTrackCount != null) {
			this.trackCount = snapshotTrackCount;
		}
		finishEditing();
	}

}
