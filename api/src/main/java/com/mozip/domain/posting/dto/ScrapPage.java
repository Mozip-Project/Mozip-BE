package com.mozip.domain.posting.dto;

import java.util.List;

import com.mozip.domain.posting.entity.Posting;

public record ScrapPage(
	List<Posting> items,
	long totalCount,
	boolean hasNext
) {
}
