package com.mozip.domain.organization.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

// 다른 도메인(공고, 역할 전환 등)이 단체 소속과 존재를 확인할 때 쓰는 연결 함수
// Security 레이어가 아닌 서비스에서 사용하는 빈으로, 로그인 여부는 GraphqlAuthInstrumentation에서 확인
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizationAccessChecker {

	private final MemberRepository memberRepository;
	private final OrganizationService organizationService;

	// 대표자, 운영진 구분 없이 단체 소속이면 true
	public boolean isOperatorOf(Long userId, Long organizationId) {
		return memberRepository.existsByOrganizationIdAndUserId(organizationId, userId);
	}

	// 대표자만 할 수 있는 일(운영진 내보내기, 대표자 인수 승인 등)의 권한 검사
	public boolean isRepresentativeOf(Long userId, Long organizationId) {
		return memberRepository.existsByOrganizationIdAndUserIdAndRole(organizationId, userId,
			MemberRole.REPRESENTATIVE);
	}

	// 없으면 ORG_404
	public Organization getOrganization(Long organizationId) {
		return organizationService.getOrganization(organizationId);
	}

}
