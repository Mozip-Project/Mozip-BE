package com.mozip.devtools.seed;

import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.OrganizationScope;
import com.mozip.domain.organization.entity.OrganizationType;
import com.mozip.domain.user.entity.User;

// 개발용 단체 seed builder
public final class SeedFixtures {

	public static final String ORGANIZATION_NAME = "개발용 단체";

	private SeedFixtures() {
	}

	// 연합 동아리로 두어 학교 데이터 없이도 만들 수 있게 함
	public static Organization organization() {
		return Organization.create(ORGANIZATION_NAME, OrganizationType.CLUB, OrganizationScope.UNION, null, false);
	}

	public static Member representative(Organization organization, User user) {
		return Member.create(organization, user, null, MemberRole.REPRESENTATIVE);
	}

}
