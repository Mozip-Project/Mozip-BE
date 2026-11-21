package com.mozip.devtools.seed;

import com.mozip.domain.organization.dto.CreateOrganizationInput;
import com.mozip.domain.organization.entity.OrganizationScope;
import com.mozip.domain.organization.entity.OrganizationType;

public final class SeedFixtures {

	public static final String ORGANIZATION_NAME = "개발용 단체";

	private SeedFixtures() {
	}

	// 학교 데이터 없이 만들 수 있는 연합 동아리
	public static CreateOrganizationInput createOrganizationInput() {
		return new CreateOrganizationInput(ORGANIZATION_NAME, OrganizationType.CLUB, null, OrganizationScope.UNION,
			null, false, null, null);
	}

}
