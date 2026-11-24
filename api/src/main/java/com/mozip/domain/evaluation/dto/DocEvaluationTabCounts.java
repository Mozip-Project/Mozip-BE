package com.mozip.domain.evaluation.dto;

public record DocEvaluationTabCounts(
	int all,
	int notEvaluated,
	int evaluated
) {
}
