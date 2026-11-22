package com.mozip.domain.interview.timetable;

import java.time.LocalDateTime;
import java.util.List;

// 자동 생성 결과 면접 한 칸, track은 1부터이고 같은 칸에는 같은 포지션 지원자만. 면접관은 운영진이 뒤에 직접 추가
// 직접 배정은 포지션이 섞일 수 있어 positionId는 이 결과에만 있고 Interview 엔티티에는 두지 않음
public record AssignedInterview(LocalDateTime startAt, LocalDateTime endAt, int track, Long positionId,
	List<Long> applicationIds) {

	public AssignedInterview {
		applicationIds = List.copyOf(applicationIds);
	}

}
