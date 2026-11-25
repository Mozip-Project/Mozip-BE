package com.mozip.domain.organization.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.mozip.domain.organization.entity.ReviewStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 심사 결과가 커밋된 뒤 대표자에게 알림, 알림 실패가 심사 결과를 되돌리지 않게 예외는 로그만 남김
@Slf4j
@Component
@RequiredArgsConstructor
public class OrganizationReviewedEventListener {

	private final OrganizationReviewNotifier reviewNotifier;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onReviewed(OrganizationReviewedEvent event) {
		try {
			if (event.reviewStatus() == ReviewStatus.APPROVED) {
				reviewNotifier.notifyApproved(event.organizationId());
			} else {
				reviewNotifier.notifyRejected(event.organizationId(), event.rejectReason());
			}
		} catch (RuntimeException e) {
			log.error("단체 심사 결과 알림 실패: organizationId={}, reviewStatus={}", event.organizationId(),
				event.reviewStatus(), e);
		}
	}

}
