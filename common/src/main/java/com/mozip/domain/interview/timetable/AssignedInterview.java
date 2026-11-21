package com.mozip.domain.interview.timetable;

import java.time.LocalDateTime;
import java.util.List;

// 배정 결과 면접 한 칸, track은 1부터이고 같은 칸에는 같은 포지션 지원자만
public record AssignedInterview(LocalDateTime startAt, LocalDateTime endAt, int track, Long positionId,
	List<Long> applicationIds, List<Long> memberIds) {

	public AssignedInterview {
		applicationIds = List.copyOf(applicationIds);
		memberIds = List.copyOf(memberIds);
	}

}
