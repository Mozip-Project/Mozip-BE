package com.mozip.domain.posting.entity;

import org.springframework.data.domain.Sort;

public enum PostingSort {

	// bumpedAt 최신순 (게시, UP 시 갱신)
	LATEST,
	CLOSING_SOON,
	POPULAR;

	// 정렬 기준 값이 같은 공고가 있으면 페이지 경계에서 순서가 흔들려 중복/누락될 수 있으므로 id로 한 번 더 끊음.
	// recruitEndAt이 null인 공고(현재는 게시 시점 검증으로 없음)도 뒤로 가도록 nullsLast를 명시함
	public Sort toJpaSort() {
		Sort.Order primary = switch (this) {
			case LATEST -> Sort.Order.desc("bumpedAt");
			case CLOSING_SOON -> Sort.Order.asc("recruitEndAt").nullsLast();
			case POPULAR -> Sort.Order.desc("viewCount");
		};
		return Sort.by(primary).and(Sort.by(Sort.Direction.DESC, "id"));
	}

}
