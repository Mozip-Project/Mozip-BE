package com.mozip.domain.payment.service;

// 이용권 연결 함수 (A 소유), 쓰는 쪽(단체 생성)은 이 인터페이스만 의존
public interface SubscriptionService {

	// 단체 생성 직후 무료 체험 기간 시작
	void startFreeTrial(Long organizationId);

}
