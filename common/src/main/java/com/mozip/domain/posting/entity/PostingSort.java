package com.mozip.domain.posting.entity;

import org.springframework.data.domain.Sort;

public enum PostingSort {

	// bumpedAt 최신순 (게시, UP 시 갱신)
	LATEST,
	CLOSING_SOON,
	POPULAR;

	public Sort toJpaSort() {
		return switch (this) {
			case LATEST -> Sort.by(Sort.Direction.DESC, "bumpedAt");
			case CLOSING_SOON -> Sort.by(Sort.Direction.ASC, "recruitEndAt");
			case POPULAR -> Sort.by(Sort.Direction.DESC, "viewCount");
		};
	}

}
