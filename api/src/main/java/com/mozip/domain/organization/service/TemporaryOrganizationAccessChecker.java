package com.mozip.domain.organization.service;

import org.springframework.stereotype.Service;

import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.exception.OrganizationErrorCode;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.repository.OrganizationRepository;
import com.mozip.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

// TODO(B 단체): B가 단체 도메인(Member, 권한 체계)을 구현하면 실제 구현으로 교체. 지금은 Member 테이블로만 운영진
// 여부를 판단함 (결정 005)
@Service
@RequiredArgsConstructor
public class TemporaryOrganizationAccessChecker implements OrganizationAccessChecker {

	private final MemberRepository memberRepository;
	private final OrganizationRepository organizationRepository;

	@Override
	public boolean isOperatorOf(Long userId, Long organizationId) {
		return memberRepository.existsByOrganizationIdAndUserId(organizationId, userId);
	}

	@Override
	public Organization getOrganization(Long organizationId) {
		return organizationRepository.findById(organizationId)
			.orElseThrow(() -> new BusinessException(OrganizationErrorCode.ORGANIZATION_NOT_FOUND)
				.addContext("organizationId", organizationId));
	}

}
