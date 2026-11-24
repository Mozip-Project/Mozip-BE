package com.mozip.domain.evaluation.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.repository.PostingRepository;

import lombok.RequiredArgsConstructor;

// 공고 단계를 넘겨도 되는지 A(PostingService)가 물어보는 창구 (결정 005)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefaultPostingResultChecker implements PostingResultChecker {

	private final PostingRepository postingRepository;

	// confirmDocResult로 확정 시각이 채워졌는지 확인. 공고가 없으면 false
	@Override
	public boolean isDocResultConfirmed(Long postingId) {
		return postingRepository.findById(postingId)
			.map(Posting::isDocResultConfirmed)
			.orElse(false);
	}

	// TODO(B 면접 평가): 최종 결과 확정을 구현하면 finalResultConfirmedAt으로 판단. 지금은 항상 true (결정 005)
	@Override
	public boolean isFinalResultConfirmed(Long postingId) {
		return true;
	}

}
