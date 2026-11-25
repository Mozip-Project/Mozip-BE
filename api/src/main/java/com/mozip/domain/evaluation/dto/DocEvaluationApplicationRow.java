package com.mozip.domain.evaluation.dto;

import com.mozip.domain.application.entity.Application;

public record DocEvaluationApplicationRow(
	Application application,
	MyDocEvaluationStatus myEvaluationStatus
) {
}
