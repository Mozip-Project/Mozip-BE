package com.mozip.domain.evaluation.dto;

public record DocResultTabCounts(
	int all,
	int pass,
	int hold,
	int fail
) {
}
