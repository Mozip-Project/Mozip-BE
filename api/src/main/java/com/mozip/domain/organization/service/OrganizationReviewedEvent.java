package com.mozip.domain.organization.service;

import com.mozip.domain.organization.entity.ReviewStatus;

// 단체 심사 완료 이벤트 (rejectReason은 미승인일 때만)
public record OrganizationReviewedEvent(
	Long organizationId,
	ReviewStatus reviewStatus,
	String rejectReason
) {
}
