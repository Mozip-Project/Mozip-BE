package com.mozip.domain.posting.exception;

// POSTING_409의 상황 구분
public enum PostingConflictReason {

	// DRAFT가 아닌 공고를 savePostingDraft로 수정하려 함, 또는 DRAFT가 아닌 공고를 deletePosting하려 함
	NOT_DRAFT,
	// 이미 게시된(DRAFT가 아닌) 공고를 다시 publishPosting하려 함
	ALREADY_PUBLISHED,
	// advancePostingStatus의 to가 정해진 다음 단계가 아님 (DRAFT·ENDED에서 호출, 순서를 건너뜀 등)
	INVALID_STATUS_TRANSITION,
	// DOC_REVIEW에서 DOC_RESULT_NOTICE로 넘기려는데 서류 결과가 아직 확정되지 않음
	DOC_RESULT_NOT_CONFIRMED,
	// FINAL_RESULT로 넘기려는데 최종 결과가 아직 확정되지 않음
	FINAL_RESULT_NOT_CONFIRMED

}
