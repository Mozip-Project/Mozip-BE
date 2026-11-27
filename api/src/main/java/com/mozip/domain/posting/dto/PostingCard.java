package com.mozip.domain.posting.dto;

import java.time.LocalDateTime;

import com.mozip.domain.posting.entity.Posting;

// organization/positions는 posting으로 PostingGraphqlController의 @BatchMapping에서 채움
public record PostingCard(
	Posting posting,
	String thumbnailUrl,
	long scrapCount,
	boolean scrapped
) {
	public Long id() {
		return posting.getId();
	}

	public String title() {
		return posting.getTitle();
	}

	public LocalDateTime recruitEndAt() {
		return posting.getRecruitEndAt();
	}

	public boolean hasInterview() {
		return posting.isHasInterview();
	}
}
