package com.mozip.domain.organization.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.OrganizationScope;
import com.mozip.domain.organization.entity.OrganizationType;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.service.OrganizationService;
import com.mozip.domain.user.entity.User;

// members는 @BatchMapping이라 Organization 목록 하나를 한 번에 받아 한 번의 쿼리로 처리해야 함(N+1 방지).
// 여기서는 Spring GraphQL 엔진 없이 그 배치 로딩/그룹핑 로직만 직접 검증한다
class OrganizationGraphqlControllerTest {

	private final OrganizationService organizationService = mock(OrganizationService.class);
	private final MemberRepository memberRepository = mock(MemberRepository.class);
	private final OrganizationGraphqlController controller = new OrganizationGraphqlController(organizationService,
		memberRepository);

	@Test
	@DisplayName("members는 organization 목록 전체를 한 번의 조회로 묶어서 반환한다")
	void batchLoadsMembers() {
		Organization orgA = organization("A");
		ReflectionTestUtils.setField(orgA, "id", 1L);
		Organization orgB = organization("B");
		ReflectionTestUtils.setField(orgB, "id", 2L);

		User user = User.create("01000000000", "encoded", "테스트", false, LocalDateTime.now());
		Member memberOfA = Member.create(orgA, user, null, MemberRole.OPERATOR);

		when(memberRepository.findByOrganizationIdIn(List.of(1L, 2L))).thenReturn(List.of(memberOfA));

		Map<Organization, List<Member>> result = controller.members(List.of(orgA, orgB));

		assertThat(result.get(orgA)).containsExactly(memberOfA);
		assertThat(result.get(orgB)).isEmpty();
	}

	private Organization organization(String name) {
		return Organization.create(name, OrganizationType.CLUB, OrganizationScope.CAMPUS, null, true);
	}

}
