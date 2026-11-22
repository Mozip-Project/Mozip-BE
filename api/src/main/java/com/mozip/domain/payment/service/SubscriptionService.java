package com.mozip.domain.payment.service;

// 이용권 연결 함수 (A 소유). 9단계(결제) 이슈에서 TemporarySubscriptionService를 실제 구현으로 교체 (결정 005)
public interface SubscriptionService {

	boolean hasActivePlan(Long organizationId);

	void startFreeTrial(Long organizationId);

}
