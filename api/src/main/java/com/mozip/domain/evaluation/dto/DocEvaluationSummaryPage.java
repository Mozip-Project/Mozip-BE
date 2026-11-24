package com.mozip.domain.evaluation.dto;

import java.util.List;

public record DocEvaluationSummaryPage(
	List<DocEvaluationSummaryRow> rows,
	int totalCount,
	DocResultTabCounts tabCounts,
	List<PositionCount> positionCounts,
	int page,
	int totalPages,
	DocResultSummary summary
) {
}
