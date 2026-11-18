package com.mozip.domain.organization.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.exception.OrganizationErrorCode;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.repository.OrganizationRepository;
import com.mozip.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

// 다른 도메인(공고, 역할 전환 등)이 단체 소속과 존재를 확인할 때 쓰는 연결 함수
// Security 레이어가 아닌 서비스에서 사용하는 빈으로, 로그인 여부는 GraphqlAuthInstrumentation에서 확인
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizationAccessChecker {

	private final MemberRepository memberRepository;
	private final OrganizationRepository organizationRepository;

	// 대표자, 운영진 구분 없이 단체 소속이면 true
	public boolean isOperatorOf(Long userId, Long organizationId) {
		return memberRepository.existsByOrganizationIdAndUserId(organizationId, userId);
	}

	public Organization getOrganization(Long organizationId) {
		return organizationRepository.findById(organizationId)
			.orElseThrow(() -> new BusinessException(OrganizationErrorCode.ORGANIZATION_NOT_FOUND)
				.addContext("organizationId", organizationId));
	}

}
