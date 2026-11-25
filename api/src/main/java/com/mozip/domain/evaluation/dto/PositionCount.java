package com.mozip.domain.evaluation.dto;

import com.mozip.domain.posting.entity.Position;

public record PositionCount(
	Position position,
	int count
) {
}
