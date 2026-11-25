package com.mozip.domain.organization.service;

import com.mozip.domain.organization.entity.CouncilLevel;
import com.mozip.domain.organization.entity.OrganizationScope;
import com.mozip.domain.organization.entity.OrganizationType;

// 단체 생성 이벤트, 커밋 후 리스너가 DB를 다시 읽지 않도록 심사에 필요한 값을 담아 발행
public record OrganizationCreatedEvent(
	Long organizationId,
	String name,
	OrganizationType type,
	CouncilLevel councilLevel,
	OrganizationScope scope,
	String schoolName,
	Long representativeUserId,
	String representativeName,
	String proofLink,
	String proofFileUrl
) {
}
