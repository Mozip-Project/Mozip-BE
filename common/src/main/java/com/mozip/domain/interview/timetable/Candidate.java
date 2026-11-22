package com.mozip.domain.interview.timetable;

import java.time.LocalDateTime;
import java.util.Set;

// 배정 대상 지원자 (서류 합격). availableTimes는 지원자가 고른 블록의 시작 시각, positionId는 포지션 없는 모집이면 null
public record Candidate(Long applicationId, Long positionId, LocalDateTime submittedAt,
	Set<LocalDateTime> availableTimes) {

	public Candidate {
		availableTimes = Set.copyOf(availableTimes);
	}

	boolean isAvailableAt(LocalDateTime startAt) {
		return availableTimes.contains(startAt);
	}

}
