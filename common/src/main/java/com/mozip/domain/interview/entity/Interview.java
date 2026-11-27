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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 타임테이블의 면접 한 칸
@Getter
@Entity
@Table(name = "interviews")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Interview extends BaseEntity {

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

	// 같은 시간대에 동시에 진행하는 면접실 열 (면접실 1, 면접실 2), 1부터
	@Column(nullable = false)
	private int track;

	// 면접 장소, 운영진이 직접 입력
	@Column(length = 100)
	private String location;

	private Interview(Posting posting, LocalDateTime startAt, LocalDateTime endAt, int track) {
		this.posting = posting;
		this.startAt = startAt;
		this.endAt = endAt;
		this.track = track;
	}

	public static Interview create(Posting posting, LocalDateTime startAt, LocalDateTime endAt, int track) {
		return new Interview(posting, startAt, endAt, track);
	}

}
