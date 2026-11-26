package com.mozip.domain.organization.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mozip.domain.school.entity.School;
import com.mozip.domain.user.entity.User;
import com.mozip.global.common.ReviewStatus;

// 단체, 운영진 생성 시 불변 조건 검증 (DB 없이)
class OrganizationEntityTest {

	private final School school = School.create("테스트대학교", "test.ac.kr");
	private final User user = User.create("01000000000", "encoded", "테스트", false, LocalDateTime.now());

	@Test
	@DisplayName("연합 단체에는 학교를 지정할 수 없다")
	void unionWithSchool() {
		assertThatThrownBy(() -> Organization.create("연합", OrganizationType.CLUB, null, OrganizationScope.UNION,
			school, false, null, null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(Organization.create("연합", OrganizationType.CLUB, null, OrganizationScope.UNION, null, false, null,
			null))
			.isNotNull();
	}

	@Test
	@DisplayName("학생회는 세부 구분이 필수고, 그 외 단체에는 세부 구분을 지정할 수 없다")
	void councilLevel() {
		assertThatThrownBy(() -> Organization.create("총학", OrganizationType.STUDENT_COUNCIL, null,
			OrganizationScope.CAMPUS, school, false, null, null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Organization.create("동아리", OrganizationType.CLUB, CouncilLevel.COLLEGE,
			OrganizationScope.CAMPUS, school, false, null, null))
			.isInstanceOf(IllegalArgumentException.class);

		Organization council = Organization.create("총학", OrganizationType.STUDENT_COUNCIL, CouncilLevel.UNIVERSITY,
			OrganizationScope.CAMPUS, school, false, null, null);

		assertThat(council.getCouncilLevel()).isEqualTo(CouncilLevel.UNIVERSITY);
		assertThat(council.getReviewStatus()).isEqualTo(ReviewStatus.PENDING);
	}

	@Test
	@DisplayName("팀 없는 단체의 운영진에는 팀을 지정할 수 없다")
	void teamInNoTeamOrganization() {
		Organization organization = campus(true);
		Team team = Team.create(organization, "기획팀", 0);

		assertThatThrownBy(() -> Member.create(organization, user, team, MemberRole.OPERATOR))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(Member.create(organization, user, null, MemberRole.OPERATOR).getTeam()).isNull();
	}

	@Test
	@DisplayName("다른 단체의 팀은 운영진에 지정할 수 없다")
	void teamOfOtherOrganization() {
		Organization organization = campus(false);
		Team otherTeam = Team.create(campus(false), "기획팀", 0);

		assertThatThrownBy(() -> Member.create(organization, user, otherTeam, MemberRole.OPERATOR))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("같은 단체의 팀은 운영진에 지정할 수 있다")
	void teamOfSameOrganization() {
		Organization organization = campus(false);
		Team team = Team.create(organization, "기획팀", 0);

		assertThat(Member.create(organization, user, team, MemberRole.OPERATOR).getTeam()).isEqualTo(team);
	}

	private Organization campus(boolean noTeam) {
		return Organization.create("교내 동아리", OrganizationType.CLUB, null, OrganizationScope.CAMPUS, school, noTeam,
			null, null);
	}

	@Test
	@DisplayName("운영진을 내보내면 leftAt이 찍히고, 이미 나간 운영진은 다시 내보낼 수 없다")
	void leave() {
		Member member = Member.create(campus(true), user, null, MemberRole.OPERATOR);
		LocalDateTime now = LocalDateTime.now();

		member.leave(now);

		assertThat(member.getLeftAt()).isEqualTo(now);
		assertThatThrownBy(() -> member.leave(now)).isInstanceOf(IllegalStateException.class);
	}

}
