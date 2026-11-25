package com.mozip.domain.posting.dto;

import java.util.List;

import com.mozip.domain.organization.entity.OrganizationType;

// TODO: fields(활동 분야) 필터는 기준 데이터(Organization.fields 등)가 아직 없어 받기만 하고 적용하지 않음
public record PostingFilter(
	String keyword,
	Long schoolId,
	List<OrganizationType> organizationTypes,
	Boolean hasInterview,
	Boolean closingSoon
) {
}
