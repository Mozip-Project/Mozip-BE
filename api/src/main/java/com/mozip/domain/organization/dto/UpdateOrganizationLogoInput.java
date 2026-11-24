package com.mozip.domain.organization.dto;

// organizationId, fileKey는 스키마의 ID!, String!이 이미 non-null을 보장하므로 별도 검증 없음
public record UpdateOrganizationLogoInput(
	Long organizationId,
	String fileKey
) {
}
