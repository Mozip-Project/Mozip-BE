package com.mozip.domain.organization.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mozip.domain.school.entity.School;
import com.mozip.domain.user.entity.User;

// 단체, 운영진 생성 시 불변 조건 검증 (DB 없이)
class OrganizationEntityTest {

	private final School school = School.create("테스트대학교", "test.ac.kr");
	private final User user = User.create("01000000000", "encoded", "테스트", false, LocalDateTime.now());

	@Test
	@DisplayName("연합 단체에는 학교를 지정할 수 없다")
	void unionWithSchool() {
		assertThatThrownBy(() -> Organization.create("연합", OrganizationType.CLUB, OrganizationScope.UNION, school,
			false))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(Organization.create("연합", OrganizationType.CLUB, OrganizationScope.UNION, null, false))
			.isNotNull();
	}

	@Test
	@DisplayName("팀 없는 단체의 운영진에는 팀을 지정할 수 없다")
	void teamInNoTeamOrganization() {
		Organization organization = campus(true);
		Team team = Team.create(organization, "기획팀");

		assertThatThrownBy(() -> Member.create(organization, user, team, MemberRole.OPERATOR))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(Member.create(organization, user, null, MemberRole.OPERATOR).getTeam()).isNull();
	}

	@Test
	@DisplayName("다른 단체의 팀은 운영진에 지정할 수 없다")
	void teamOfOtherOrganization() {
		Organization organization = campus(false);
		Team otherTeam = Team.create(campus(false), "기획팀");

		assertThatThrownBy(() -> Member.create(organization, user, otherTeam, MemberRole.OPERATOR))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("같은 단체의 팀은 운영진에 지정할 수 있다")
	void teamOfSameOrganization() {
		Organization organization = campus(false);
		Team team = Team.create(organization, "기획팀");

		assertThat(Member.create(organization, user, team, MemberRole.OPERATOR).getTeam()).isEqualTo(team);
	}

	private Organization campus(boolean noTeam) {
		return Organization.create("교내 동아리", OrganizationType.CLUB, OrganizationScope.CAMPUS, school, noTeam);
	}

}
