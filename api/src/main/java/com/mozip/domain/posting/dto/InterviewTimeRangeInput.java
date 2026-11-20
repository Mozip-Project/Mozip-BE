package com.mozip.domain.posting.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record InterviewTimeRangeInput(
	// null = 포지션 없는 모집 또는 전체 공통. 2단계에서 먼저 저장된 Position의 실제 id를 가리킴
	Long positionId,
	LocalDate date,
	LocalTime startTime,
	LocalTime endTime,
	List<LocalTime> closedSlots
) {
}
