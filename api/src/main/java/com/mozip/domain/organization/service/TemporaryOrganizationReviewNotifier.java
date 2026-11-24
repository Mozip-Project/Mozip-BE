package com.mozip.domain.organization.service;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

// TODO(A 알림 #48): NotificationService가 들어오면 단체 대표자에게 승인/미승인 알림을 보내는 실제 구현으로 교체. 지금은 로그만 남김
@Slf4j
@Service
public class TemporaryOrganizationReviewNotifier implements OrganizationReviewNotifier {

	@Override
	public void notifyApproved(Long organizationId) {
		log.info("단체 승인 알림 임시 구현, 로그만 남김: organizationId={}", organizationId);
	}

	@Override
	public void notifyRejected(Long organizationId, String rejectReason) {
		log.info("단체 미승인 알림 임시 구현, 로그만 남김: organizationId={}", organizationId);
	}

}
