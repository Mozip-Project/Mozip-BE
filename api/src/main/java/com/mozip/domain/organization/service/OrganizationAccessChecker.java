package com.mozip.domain.organization.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

// 다른 도메인(공고, 역할 전환 등)이 단체 존재와 소속을 확인할 때 쓰는 함수
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizationAccessChecker {

	private final MemberRepository memberRepository;
	private final OrganizationService organizationService;

	// 대표자, 운영진 구분 없이 단체 소속이면 true. 내보내진 운영진(leftAt)은 소속이 아님
	public boolean isOperatorOf(Long userId, Long organizationId) {
		return memberRepository.existsByOrganizationIdAndUserIdAndLeftAtIsNull(organizationId, userId);
	}

	// 단체 대표자면 true
	public boolean isRepresentativeOf(Long userId, Long organizationId) {
		return memberRepository.existsByOrganizationIdAndUserIdAndRoleAndLeftAtIsNull(organizationId, userId,
			MemberRole.REPRESENTATIVE);
	}

	// 없으면 ORG_404
	public Organization getOrganization(Long organizationId) {
		return organizationService.getOrganization(organizationId);
	}

}
