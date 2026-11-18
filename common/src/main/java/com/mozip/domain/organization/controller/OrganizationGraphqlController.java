package com.mozip.domain.organization.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import com.mozip.domain.organization.dto.CreateOrganizationInput;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.Team;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.repository.TeamRepository;
import com.mozip.domain.organization.service.OrganizationService;
import com.mozip.global.security.AuthUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class OrganizationGraphqlController {

	private final OrganizationService organizationService;
	private final TeamRepository teamRepository;
	private final MemberRepository memberRepository;

	@MutationMapping
	public Organization createOrganization(@Argument @Valid CreateOrganizationInput input,
		@AuthenticationPrincipal AuthUser authUser) {
		return organizationService.createOrganization(authUser.userId(), input);
	}

	@QueryMapping
	public List<Organization> myOrganizations(@AuthenticationPrincipal AuthUser authUser) {
		return organizationService.getMyOrganizations(authUser.userId());
	}

	@QueryMapping
	public Organization organization(@Argument Long id) {
		return organizationService.getOrganization(id);
	}

	// 단체 N개를 응답 시 배치 조회
	@BatchMapping(typeName = "Organization")
	public Map<Organization, List<Team>> teams(List<Organization> organizations) {
		return groupByOrganization(organizations, teamRepository.findAllByOrganizationInOrderById(organizations),
			Team::getOrganization);
	}

	// members[].user, members[].team join fetch를 위한 배치 조회
	@BatchMapping(typeName = "Organization")
	public Map<Organization, List<Member>> members(List<Organization> organizations) {
		return groupByOrganization(organizations,
			memberRepository.findAllWithUserAndTeamByOrganizationIn(organizations), Member::getOrganization);
	}

	// 조회된 항목의 organization은 프록시일 수 있기 때문에 id로 조회하여 결과 Map의 키는 GraphQL이 넘긴 부모 객체 그대로 사용
	private static <T> Map<Organization, List<T>> groupByOrganization(List<Organization> organizations,
		List<T> items, Function<T, Organization> owner) {
		Map<Long, List<T>> byOrganizationId = items.stream()
			.collect(Collectors.groupingBy(item -> owner.apply(item).getId()));
		Map<Organization, List<T>> result = new LinkedHashMap<>();
		for (Organization organization : organizations) {
			result.put(organization, byOrganizationId.getOrDefault(organization.getId(), List.of()));
		}
		return result;
	}

}
