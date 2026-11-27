package com.mozip.domain.interview.entity;

import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 확정 후 수정 모드에 들어갈 때 떠 둔 면접 칸 사본. 수정 취소하면 이걸로 되돌림
@Getter
@Entity
@Table(name = "interview_snapshots")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterviewSnapshot extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "posting_id", nullable = false)
	private Posting posting;

	@Column(nullable = false)
	private LocalDateTime startAt;

	@Column(nullable = false)
	private LocalDateTime endAt;

	@Column(nullable = false)
	private int track;

	@Column(length = 100)
	private String location;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(columnDefinition = "bigint[]", nullable = false)
	private List<Long> applicationIds;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(columnDefinition = "bigint[]", nullable = false)
	private List<Long> memberIds;

	private InterviewSnapshot(Interview interview, List<Long> applicationIds, List<Long> memberIds) {
		this.posting = interview.getPosting();
		this.startAt = interview.getStartAt();
		this.endAt = interview.getEndAt();
		this.track = interview.getTrack();
		this.location = interview.getLocation();
		this.applicationIds = applicationIds;
		this.memberIds = memberIds;
	}

	public static InterviewSnapshot of(Interview interview, List<Long> applicationIds, List<Long> memberIds) {
		return new InterviewSnapshot(interview, applicationIds, memberIds);
	}

}
