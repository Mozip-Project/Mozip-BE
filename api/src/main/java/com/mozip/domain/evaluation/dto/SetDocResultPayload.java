package com.mozip.domain.evaluation.dto;

import com.mozip.domain.application.entity.Application;
import com.mozip.domain.application.entity.DocResult;

public record SetDocResultPayload(
	Application application,
	DocResult docResult,
	DocResultSummary summary
) {
}
