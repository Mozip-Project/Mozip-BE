package com.mozip.domain.posting.dto;

public record PositionInput(
	Long teamId,
	String name,
	Integer headcount,
	int sortOrder
) {
}
