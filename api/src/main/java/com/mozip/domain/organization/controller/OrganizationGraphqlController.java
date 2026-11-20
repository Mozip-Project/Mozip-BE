package com.mozip.domain.organization.controller;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import com.mozip.domain.organization.dto.CreateOrganizationInput;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.Team;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.repository.TeamRepository;
import com.mozip.domain.organization.service.OrganizationService;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;
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
	public Organization organization(@Argument Long id, @AuthenticationPrincipal AuthUser authUser) {
		return organizationService.getVisibleOrganization(id, authUser == null ? null : authUser.userId());
	}

	// 단체 N개를 응답 시 배치 조회
	@BatchMapping(typeName = "Organization")
	public Map<Organization, List<Team>> teams(List<Organization> organizations) {
		return groupByOrganization(organizations, teamRepository.findAllByOrganizationInOrderById(organizations),
			Team::getOrganization);
	}

	// 운영진 명단은 회원 연락처가 담겨 소속 운영진만 조회. 단체 상세가 공개라 최상위 인증 검사만으로는 막히지 않음
	@BatchMapping(typeName = "Organization")
	public Map<Organization, List<Member>> members(List<Organization> organizations, Principal principal) {
		Set<Long> manageable = organizationService.getManageableOrganizationIds(userId(principal), organizations);
		if (!organizations.stream().allMatch(organization -> manageable.contains(organization.getId()))) {
			throw new BusinessException(CommonErrorCode.FORBIDDEN);
		}
		return groupByOrganization(organizations,
			memberRepository.findAllWithUserAndTeamByOrganizationIn(organizations), Member::getOrganization);
	}

	// 심사 증빙은 소속 운영진에게만 보이고 그 외에는 null
	@BatchMapping(typeName = "Organization")
	public Map<Organization, String> proofLink(List<Organization> organizations, Principal principal) {
		return manageableOnly(organizations, principal, Organization::getProofLink);
	}

	@BatchMapping(typeName = "Organization")
	public Map<Organization, String> proofFileUrl(List<Organization> organizations, Principal principal) {
		return manageableOnly(organizations, principal, Organization::getProofFileUrl);
	}

	// 값이 null이면 Map에 넣지 않음. 빠진 키는 GraphQL이 null로 응답
	private Map<Organization, String> manageableOnly(List<Organization> organizations, Principal principal,
		Function<Organization, String> field) {
		Set<Long> manageable = organizationService.getManageableOrganizationIds(userId(principal), organizations);
		Map<Organization, String> result = new LinkedHashMap<>();
		for (Organization organization : organizations) {
			String value = field.apply(organization);
			if (manageable.contains(organization.getId()) && value != null) {
				result.put(organization, value);
			}
		}
		return result;
	}

	// 배치 매핑은 @AuthenticationPrincipal을 받지 못해 Principal에서 꺼냄. 비로그인이면 null
	private static Long userId(Principal principal) {
		if (principal instanceof Authentication authentication
			&& authentication.getPrincipal() instanceof AuthUser authUser) {
			return authUser.userId();
		}
		return null;
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
