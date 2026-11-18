package com.mozip.domain.payment.service;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

// TODO(A 이용권): 이용권 모델이 들어오면 실제 구현으로 교체. 그때 SeedRunner의 개발용 단체도 무제한 이용권으로
@Slf4j
@Service
public class TemporarySubscriptionService implements SubscriptionService {

	@Override
	public void startFreeTrial(Long organizationId) {
		log.debug("startFreeTrial 임시 구현, 아무것도 하지 않음: organizationId={}", organizationId);
	}

}
