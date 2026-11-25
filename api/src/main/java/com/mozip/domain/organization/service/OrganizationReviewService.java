package com.mozip.domain.organization.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.ReviewStatus;
import com.mozip.domain.organization.exception.OrganizationConflictReason;
import com.mozip.domain.organization.exception.OrganizationErrorCode;
import com.mozip.domain.organization.repository.OrganizationRepository;
import com.mozip.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

// 단체 등록 심사 (승인, 미승인). 호출자 권한은 부르는 쪽(디스코드 Interactions)에서 검사
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizationReviewService {

	private final OrganizationRepository organizationRepository;
	private final ApplicationEventPublisher eventPublisher;

	// 없으면 ORG_404, 이미 심사가 끝났으면 ORG_409 ALREADY_REVIEWED
	@Transactional
	public Organization approve(Long organizationId) {
		Organization organization = lockPending(organizationId);
		organization.approve();
		eventPublisher.publishEvent(new OrganizationReviewedEvent(organizationId, ReviewStatus.APPROVED, null));
		return organization;
	}

	@Transactional
	public Organization reject(Long organizationId, String reason) {
		Organization organization = lockPending(organizationId);
		organization.reject(reason);
		eventPublisher.publishEvent(new OrganizationReviewedEvent(organizationId, ReviewStatus.REJECTED, reason));
		return organization;
	}

	public ReviewStatus getReviewStatus(Long organizationId) {
		return organizationRepository.findById(organizationId)
			.orElseThrow(() -> notFound(organizationId))
			.getReviewStatus();
	}

	// 단체 행을 잠근 뒤 상태를 확인해 동시에 두 번 눌러도 먼저 잠근 쪽만 처리
	private Organization lockPending(Long organizationId) {
		Organization organization = organizationRepository.lockById(organizationId)
			.orElseThrow(() -> notFound(organizationId));
		if (!organization.isPending()) {
			throw new BusinessException(OrganizationErrorCode.CONFLICT, OrganizationConflictReason.ALREADY_REVIEWED)
				.addContext("organizationId", organizationId)
				.addContext("reviewStatus", organization.getReviewStatus());
		}
		return organization;
	}

	private static BusinessException notFound(Long organizationId) {
		return new BusinessException(OrganizationErrorCode.ORGANIZATION_NOT_FOUND)
			.addContext("organizationId", organizationId);
	}

}
