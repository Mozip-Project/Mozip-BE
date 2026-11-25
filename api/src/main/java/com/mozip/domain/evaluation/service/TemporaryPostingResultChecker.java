package com.mozip.domain.evaluation.service;

import org.springframework.stereotype.Service;

// TODO(B 서류 평가/면접): B가 서류 평가 결과 취합·면접 결과 확정을 구현하면 실제 구현으로 교체. 지금은 항상 true (결정 005)
@Service
public class TemporaryPostingResultChecker implements PostingResultChecker {

	@Override
	public boolean isDocResultConfirmed(Long postingId) {
		return true;
	}

	@Override
	public boolean isFinalResultConfirmed(Long postingId) {
		return true;
	}

}
