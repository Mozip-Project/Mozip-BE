package com.mozip.domain.posting.entity;

// 비면접 공고는 INTERVIEW 단계를 건너뜀
public enum PostingStatus {

	DRAFT,
	RECRUITING,
	DOC_REVIEW,
	DOC_RESULT_NOTICE,
	INTERVIEW,
	FINAL_RESULT,
	ENDED;

	// 지원자에게 서류 결과를 보여도 되는 단계
	public boolean isDocResultVisible() {
		return this == DOC_RESULT_NOTICE || this == INTERVIEW || this == FINAL_RESULT || this == ENDED;
	}

	// 지원자에게 최종 결과를 보여도 되는 단계
	public boolean isFinalResultVisible() {
		return this == FINAL_RESULT || this == ENDED;
	}

}
