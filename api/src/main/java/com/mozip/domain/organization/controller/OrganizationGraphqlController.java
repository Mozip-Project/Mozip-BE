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
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import com.mozip.domain.organization.dto.CreateOrganizationInput;
import com.mozip.domain.organization.dto.TeamInput;
import com.mozip.domain.organization.dto.UpdateOrganizationInput;
import com.mozip.domain.organization.entity.Invitation;
import com.mozip.domain.organization.entity.InviteLink;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.OrganizationField;
import com.mozip.domain.organization.entity.OrganizationFieldTag;
import com.mozip.domain.organization.entity.OrganizationKeyword;
import com.mozip.domain.organization.entity.OrganizationLink;
import com.mozip.domain.organization.entity.Team;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.repository.OrganizationFieldTagRepository;
import com.mozip.domain.organization.repository.OrganizationKeywordRepository;
import com.mozip.domain.organization.repository.OrganizationLinkRepository;
import com.mozip.domain.organization.repository.TeamRepository;
import com.mozip.domain.organization.service.InvitationService;
import com.mozip.domain.organization.service.InviteProperties;
import com.mozip.domain.organization.service.MemberService;
import com.mozip.domain.organization.service.OrganizationService;
import com.mozip.domain.organization.service.TeamService;
import com.mozip.domain.user.entity.User;
import com.mozip.global.security.AuthUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class OrganizationGraphqlController {

	private final OrganizationService organizationService;
	private final MemberService memberService;
	private final InvitationService invitationService;
	private final TeamService teamService;
	private final InviteProperties inviteProperties;
	private final TeamRepository teamRepository;
	private final MemberRepository memberRepository;
	private final OrganizationKeywordRepository keywordRepository;
	private final OrganizationFieldTagRepository fieldTagRepository;
	private final OrganizationLinkRepository linkRepository;

	@MutationMapping
	public Organization createOrganization(@Argument @Valid CreateOrganizationInput input,
		@AuthenticationPrincipal AuthUser authUser) {
		return organizationService.createOrganization(authUser.userId(), input);
	}

	@MutationMapping
	public Organization updateOrganization(@Argument Long id, @Argument @Valid UpdateOrganizationInput input,
		@AuthenticationPrincipal AuthUser authUser) {
		return organizationService.updateOrganization(authUser.userId(), id, input);
	}

	// 반환 타입 Me는 User 엔티티로 그림 (UserGraphqlController의 me와 같음)
	@MutationMapping
	public User selectOrganization(@Argument Long organizationId, @AuthenticationPrincipal AuthUser authUser) {
		return organizationService.selectOrganization(authUser.userId(), organizationId);
	}

	@QueryMapping
	public Organization organization(@Argument Long id) {
		return organizationService.getOrganization(id);
	}

	@QueryMapping
	public List<String> keywordSuggestions(@Argument String prefix) {
		return organizationService.getKeywordSuggestions(prefix);
	}

	// 운영진 관리 표. 목록 한 번에 단체 하나라 배치 조회 없이 user/team을 join fetch
	@QueryMapping
	public List<Member> members(@Argument Long organizationId, @AuthenticationPrincipal AuthUser authUser) {
		return memberService.getMembers(authUser.userId(), organizationId);
	}

	// 성공하면 true, 실패는 전부 errors[]로 가므로 false를 돌려줄 일은 없음
	@MutationMapping
	public boolean removeMember(@Argument Long memberId, @AuthenticationPrincipal AuthUser authUser) {
		memberService.removeMember(authUser.userId(), memberId);
		return true;
	}

	@MutationMapping
	public Invitation inviteMember(@Argument Long organizationId, @Argument String contact, @Argument Long teamId,
		@AuthenticationPrincipal AuthUser authUser) {
		return invitationService.inviteMember(authUser.userId(), organizationId, contact, teamId);
	}

	@MutationMapping
	public InviteLink createInviteLink(@Argument Long organizationId, @AuthenticationPrincipal AuthUser authUser) {
		return invitationService.createInviteLink(authUser.userId(), organizationId);
	}

	@MutationMapping
	public Member acceptInvitation(@Argument String code, @AuthenticationPrincipal AuthUser authUser) {
		return invitationService.acceptInvitation(authUser.userId(), code);
	}

	@MutationMapping
	public List<Team> saveTeams(@Argument Long organizationId, @Argument List<TeamInput> teams,
		@AuthenticationPrincipal AuthUser authUser) {
		return teamService.saveTeams(authUser.userId(), organizationId, teams);
	}

	// 링크 주소는 저장하지 않고 응답할 때 설정의 base-url과 토큰을 이어 붙임 (도메인이 바뀌어도 데이터 수정 없음)
	@SchemaMapping(typeName = "InviteLink")
	public String url(InviteLink link) {
		return inviteProperties.linkUrl(link.getToken());
	}

	// 단체 N개를 응답 시 배치 조회
	@BatchMapping(typeName = "Organization")
	public Map<Organization, List<Team>> teams(List<Organization> organizations) {
		return groupByOrganization(organizations, teamRepository.findAllByOrganizationInOrderBySortOrderAscIdAsc(organizations),
			Team::getOrganization, Function.identity());
	}

	// members[].user, members[].team join fetch를 위한 배치 조회
	@BatchMapping(typeName = "Organization")
	public Map<Organization, List<Member>> members(List<Organization> organizations) {
		return groupByOrganization(organizations,
			memberRepository.findAllWithUserAndTeamByOrganizationIn(organizations), Member::getOrganization,
			Function.identity());
	}

	@BatchMapping(typeName = "Organization")
	public Map<Organization, List<String>> keywords(List<Organization> organizations) {
		return groupByOrganization(organizations, keywordRepository.findAllByOrganizationInOrderById(organizations),
			OrganizationKeyword::getOrganization, OrganizationKeyword::getKeyword);
	}

	@BatchMapping(typeName = "Organization")
	public Map<Organization, List<OrganizationField>> fields(List<Organization> organizations) {
		return groupByOrganization(organizations, fieldTagRepository.findAllByOrganizationInOrderById(organizations),
			OrganizationFieldTag::getOrganization, OrganizationFieldTag::getField);
	}

	@BatchMapping(typeName = "Organization")
	public Map<Organization, List<OrganizationLink>> links(List<Organization> organizations) {
		return groupByOrganization(organizations, linkRepository.findAllByOrganizationInOrderById(organizations),
			OrganizationLink::getOrganization, Function.identity());
	}

	// 조회된 항목의 organization은 프록시일 수 있기 때문에 id로 묶고, 결과 Map의 키는 GraphQL이 넘긴 부모 객체 그대로 사용
	// value로 엔티티 대신 응답할 값(키워드 문자열 등)을 골라낼 수 있음
	private static <T, R> Map<Organization, List<R>> groupByOrganization(List<Organization> organizations,
		List<T> items, Function<T, Organization> owner, Function<T, R> value) {
		Map<Long, List<R>> byOrganizationId = items.stream()
			.collect(Collectors.groupingBy(item -> owner.apply(item).getId(),
				Collectors.mapping(value, Collectors.toList())));
		Map<Organization, List<R>> result = new LinkedHashMap<>();
		for (Organization organization : organizations) {
			result.put(organization, byOrganizationId.getOrDefault(organization.getId(), List.of()));
		}
		return result;
	}

}
