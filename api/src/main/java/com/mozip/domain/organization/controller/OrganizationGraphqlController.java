package com.mozip.domain.organization.controller;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import com.mozip.domain.organization.dto.UpdateOrganizationLogoInput;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.service.OrganizationService;
import com.mozip.global.security.AuthUser;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class OrganizationGraphqlController {

	private final OrganizationService organizationService;
	private final MemberRepository memberRepository;

	@MutationMapping
	public Organization updateOrganizationLogo(@Argument UpdateOrganizationLogoInput input,
		@AuthenticationPrincipal AuthUser authUser) {
		return organizationService.updateLogo(input.organizationId(), input.fileKey(), authUser.userId());
	}

	// 같은 레벨에서 조회된 Organization 목록(예: me.organizations)을 모아 한 번에 배치로 넘겨받음.
	// Member.organization은 다른 세션에서 로딩된 별개 인스턴스라 식별자로 맞춰 매핑해야 함
	@BatchMapping(typeName = "Organization", field = "members")
	public Map<Organization, List<Member>> members(List<Organization> organizations) {
		List<Long> organizationIds = organizations.stream().map(Organization::getId).toList();

		Map<Long, List<Member>> membersByOrganizationId = memberRepository.findByOrganizationIdIn(organizationIds)
			.stream()
			.collect(Collectors.groupingBy(member -> member.getOrganization().getId()));

		return organizations.stream()
			.collect(Collectors.toMap(
				Function.identity(),
				organization -> membersByOrganizationId.getOrDefault(organization.getId(), List.of())));
	}

}
