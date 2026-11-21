package com.mozip.domain.payment.service;

import org.springframework.stereotype.Service;

// TODO(A 이용권): 9단계(결제) 이슈에서 실제 구현으로 교체. hasActivePlan은 항상 true, startFreeTrial은 아무 것도 안 함
@Service
public class TemporarySubscriptionService implements SubscriptionService {

	@Override
	public boolean hasActivePlan(Long organizationId) {
		return true;
	}

	@Override
	public void startFreeTrial(Long organizationId) {
	}

}
