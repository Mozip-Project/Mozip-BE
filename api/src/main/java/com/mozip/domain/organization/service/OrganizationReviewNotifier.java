package com.mozip.domain.organization.service;

// 심사 결과를 단체 대표자에게 알리는 연결 함수 (A 알림 소유). 알림 #48이 들어오면 TemporaryOrganizationReviewNotifier를 실제 구현으로 교체 (결정 005)
public interface OrganizationReviewNotifier {

	void notifyApproved(Long organizationId);

	void notifyRejected(Long organizationId, String rejectReason);

}
