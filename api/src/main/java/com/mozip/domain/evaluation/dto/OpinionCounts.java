package com.mozip.domain.evaluation.dto;

// 저장된 평가의 의견별 운영진 수. notEvaluated는 아직 저장하지 않은 운영진 수
public record OpinionCounts(
	int pass,
	int hold,
	int fail,
	int notEvaluated
) {
}
