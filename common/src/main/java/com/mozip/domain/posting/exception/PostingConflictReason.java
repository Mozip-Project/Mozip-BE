package com.mozip.domain.posting.exception;

// POSTING_409의 상황 구분
public enum PostingConflictReason {

	// DRAFT가 아닌 공고를 savePostingDraft로 수정하려 함
	NOT_DRAFT,
	// 이미 게시된(DRAFT가 아닌) 공고를 다시 publishPosting하려 함
	ALREADY_PUBLISHED

}
