package com.mozip.domain.evaluation.dto;

import java.time.LocalDateTime;

// 공고 전체 서류 결과 현황 (제출된 지원서 기준)
public record DocResultSummary(
	int totalCount,
	int evaluatedCount,
	boolean allEvaluated,
	int passCount,
	int holdCount,
	int failCount,
	int pendingCount,
	boolean canConfirm,
	LocalDateTime docResultConfirmedAt
) {
}
