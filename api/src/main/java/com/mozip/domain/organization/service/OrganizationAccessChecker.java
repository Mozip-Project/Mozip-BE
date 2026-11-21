package com.mozip.domain.organization.service;

import com.mozip.domain.organization.entity.Organization;

// B 소유 인터페이스. B가 단체 도메인을 구현하면 TemporaryOrganizationAccessChecker를 실제 구현으로 교체함 (결정 005)
public interface OrganizationAccessChecker {

	boolean isOperatorOf(Long userId, Long organizationId);

	// 없으면 ORG_404
	Organization getOrganization(Long organizationId);

}
