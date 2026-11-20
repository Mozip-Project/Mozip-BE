package com.mozip.domain.posting.dto;

import java.util.List;

import com.mozip.domain.posting.entity.Posting;

public record PostingPage(
	List<Posting> items,
	long totalCount,
	boolean hasNext
) {
}
