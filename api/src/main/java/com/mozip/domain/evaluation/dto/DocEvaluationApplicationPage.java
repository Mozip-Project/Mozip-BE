package com.mozip.domain.evaluation.dto;

import java.util.List;

public record DocEvaluationApplicationPage(
	List<DocEvaluationApplicationRow> rows,
	// 탭, 포지션 필터, 검색어를 모두 적용한 건수
	int totalCount,
	// 포지션 필터와 검색어만 적용
	DocEvaluationTabCounts tabCounts,
	// 검색어만 적용
	List<PositionCount> positionCounts,
	int page,
	int totalPages
) {
}
