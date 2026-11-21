package com.mozip.domain.evaluation.service;

// B 소유 인터페이스. B가 서류 평가/면접 결과 확정을 구현하면 TemporaryPostingResultChecker를 실제 구현으로 교체함 (결정 005)
public interface PostingResultChecker {

	// 서류 평가 결과가 확정됐는지 (advancePostingStatus의 DOC_REVIEW -> DOC_RESULT_NOTICE 조건)
	boolean isDocResultConfirmed(Long postingId);

	// 최종 결과가 확정됐는지 (advancePostingStatus의 FINAL_RESULT 전환 조건, 면접 없는 공고도 동일)
	boolean isFinalResultConfirmed(Long postingId);

}
