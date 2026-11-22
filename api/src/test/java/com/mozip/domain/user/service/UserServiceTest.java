package com.mozip.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.mozip.domain.auth.exception.AuthErrorCode;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.OrganizationScope;
import com.mozip.domain.organization.entity.OrganizationType;
import com.mozip.domain.organization.exception.OrganizationErrorCode;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.repository.OrganizationRepository;
import com.mozip.domain.user.dto.Me;
import com.mozip.domain.user.entity.Role;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.entity.UserStatus;
import com.mozip.domain.user.exception.UserErrorCode;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;

class UserServiceTest {

	private final UserRepository userRepository = mock(UserRepository.class);
	private final OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
	private final MemberRepository memberRepository = mock(MemberRepository.class);
	private final UserService userService = new UserService(userRepository, organizationRepository, memberRepository);

	private User user;

	@BeforeEach
	void setUp() {
		user = User.create("01000000000", "encoded", "테스트", false, LocalDateTime.now());
		ReflectionTestUtils.setField(user, "id", 1L);
		when(userRepository.findByIdWithAssociations(1L)).thenReturn(Optional.of(user));
		when(memberRepository.findByUserId(1L)).thenReturn(List.of());
	}

	@Test
	@DisplayName("존재하지 않는 회원이면 USER_404를 던진다")
	void userNotFound() {
		when(userRepository.findByIdWithAssociations(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.me(999L))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(UserErrorCode.NOT_FOUND);
	}

	@Test
	@DisplayName("잠기거나 탈퇴한 회원은 me 조회 시 AUTH_005로 막는다")
	void inactiveUserCannotFetchMe() {
		ReflectionTestUtils.setField(user, "status", UserStatus.WITHDRAWN);

		assertThatThrownBy(() -> userService.me(1L))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(AuthErrorCode.INACTIVE_USER);
	}

	@Test
	@DisplayName("잠기거나 탈퇴한 회원은 switchRole도 AUTH_005로 막는다")
	void inactiveUserCannotSwitchRole() {
		ReflectionTestUtils.setField(user, "status", UserStatus.LOCKED);

		assertThatThrownBy(() -> userService.switchRole(1L, Role.APPLICANT, null))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(AuthErrorCode.INACTIVE_USER);
	}

	@Test
	@DisplayName("me는 소속 단체 목록과 알림 기본값을 담아 반환한다")
	void me() {
		Organization organization = organization();
		Member member = Member.create(organization, user, null, MemberRole.OPERATOR);
		when(memberRepository.findByUserId(1L)).thenReturn(List.of(member));

		Me me = userService.me(1L);

		assertThat(me.id()).isEqualTo(1L);
		assertThat(me.organizations()).containsExactly(organization);
		assertThat(me.notificationSettings().notifyResult()).isTrue();
		assertThat(me.unreadNotificationCount()).isZero();
	}

	@Test
	@DisplayName("OPERATOR 전환 시 organizationId가 없으면 COMMON_400을 던진다")
	void switchRoleWithoutOrganizationId() {
		assertThatThrownBy(() -> userService.switchRole(1L, Role.OPERATOR, null))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(CommonErrorCode.INVALID_INPUT);
	}

	@Test
	@DisplayName("OPERATOR 전환 시 단체가 없으면 ORG_404를 던진다")
	void switchRoleOrganizationNotFound() {
		when(organizationRepository.findByIdWithSchool(10L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.switchRole(1L, Role.OPERATOR, 10L))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(OrganizationErrorCode.ORGANIZATION_NOT_FOUND);
	}

	@Test
	@DisplayName("OPERATOR 전환 시 그 단체의 운영진이 아니면 COMMON_403을 던진다")
	void switchRoleNotMember() {
		Organization organization = organization();
		when(organizationRepository.findByIdWithSchool(10L)).thenReturn(Optional.of(organization));
		when(memberRepository.existsByOrganizationIdAndUserId(10L, 1L)).thenReturn(false);

		assertThatThrownBy(() -> userService.switchRole(1L, Role.OPERATOR, 10L))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(CommonErrorCode.FORBIDDEN);
	}

	@Test
	@DisplayName("OPERATOR 전환에 성공하면 lastOrganization과 lastRole이 같이 바뀐다")
	void switchRoleToOperator() {
		Organization organization = organization();
		when(organizationRepository.findByIdWithSchool(10L)).thenReturn(Optional.of(organization));
		when(memberRepository.existsByOrganizationIdAndUserId(10L, 1L)).thenReturn(true);

		Me me = userService.switchRole(1L, Role.OPERATOR, 10L);

		assertThat(me.lastRole()).isEqualTo(Role.OPERATOR);
		assertThat(me.lastOrganization()).isEqualTo(organization);
	}

	@Test
	@DisplayName("APPLICANT로 전환하면 organizationId를 무시하고 이전 lastOrganization을 그대로 유지한다")
	void switchRoleToApplicantKeepsLastOrganization() {
		Organization organization = organization();
		ReflectionTestUtils.setField(user, "lastOrganization", organization);

		Me me = userService.switchRole(1L, Role.APPLICANT, 999L);

		assertThat(me.lastRole()).isEqualTo(Role.APPLICANT);
		assertThat(me.lastOrganization()).isEqualTo(organization);
	}

	private Organization organization() {
		return Organization.create("테스트 단체", OrganizationType.CLUB, null, OrganizationScope.CAMPUS, null, true,
			null, null);
	}

}
