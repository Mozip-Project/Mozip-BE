package com.mozip.domain.interview.timetable;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

// 운영진이 연 하루치 면접 가능 구간. closedSlots는 그 구간 안에서 운영진이 끈 블록의 시작 시각
public record TimeRange(LocalDate date, LocalTime startTime, LocalTime endTime, Set<LocalTime> closedSlots) {

	public TimeRange {
		if (!startTime.isBefore(endTime)) {
			throw new IllegalArgumentException("면접 가능 구간의 시작은 끝보다 앞이어야 합니다.");
		}
		closedSlots = Set.copyOf(closedSlots);
	}

}
