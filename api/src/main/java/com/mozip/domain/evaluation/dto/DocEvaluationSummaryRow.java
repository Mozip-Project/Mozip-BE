package com.mozip.domain.evaluation.dto;

import com.mozip.domain.application.entity.Application;
import com.mozip.domain.application.entity.DocResult;

// 취합 목록의 지원자 한 줄. docResult는 Application.docResult 리졸버가 가리기 전의 실제 값
public record DocEvaluationSummaryRow(
	Application application,
	DocResult docResult,
	Integer rank,
	Double averageScore,
	OpinionCounts opinionCounts,
	boolean evaluationComplete
) {
}
