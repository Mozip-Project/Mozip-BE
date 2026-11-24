package com.mozip.domain.evaluation.dto;

import com.mozip.domain.posting.entity.Posting;

public record ConfirmDocResultPayload(
	Posting posting,
	DocResultSummary summary
) {
}
