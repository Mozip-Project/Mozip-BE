package com.mozip.domain.application.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ApplicationInput(
	// null = 포지션 없는 모집
	Long positionId,
	List<AnswerInput> answers,
	// 면접 가능 시간 블록의 시작 시각 목록. 면접 없는 공고면 무시
	List<LocalDateTime> availableTimes
) {
}
