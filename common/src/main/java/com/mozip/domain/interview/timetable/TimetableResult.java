package com.mozip.domain.interview.timetable;

import java.util.List;

// 배정 결과. unassignedApplicationIds는 서류 합격인데 어느 칸에도 못 들어간 지원자 (화면의 미배정 목록)
public record TimetableResult(List<AssignedInterview> interviews, List<Long> unassignedApplicationIds) {

	public TimetableResult {
		interviews = List.copyOf(interviews);
		unassignedApplicationIds = List.copyOf(unassignedApplicationIds);
	}

}
