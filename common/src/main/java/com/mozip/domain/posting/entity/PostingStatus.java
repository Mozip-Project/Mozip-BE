package com.mozip.domain.posting.entity;

// 비면접 공고는 INTERVIEW 단계를 건너뜀
public enum PostingStatus {

	DRAFT,
	RECRUITING,
	DOC_REVIEW,
	DOC_RESULT_NOTICE,
	INTERVIEW,
	FINAL_RESULT,
	ENDED

}
