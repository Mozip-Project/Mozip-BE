package com.mozip.domain.posting.dto;

import java.util.List;

public record PostingPage(
	List<PostingCard> items,
	long totalCount,
	boolean hasNext
) {
}
