package com.mozip.domain.evaluation.service;

// B 소유 인터페이스. 구현은 DefaultPostingResultChecker이고 서류는 실제 확정 여부, 최종 결과는 아직 항상 true (결정 005)
public interface PostingResultChecker {

	// 서류 평가 결과가 확정됐는지 (advancePostingStatus의 DOC_REVIEW -> DOC_RESULT_NOTICE 조건)
	boolean isDocResultConfirmed(Long postingId);

	// 최종 결과가 확정됐는지 (advancePostingStatus의 FINAL_RESULT 전환 조건, 면접 없는 공고도 동일)
	boolean isFinalResultConfirmed(Long postingId);

}
