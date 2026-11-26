package com.mozip.domain.organization.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.domain.organization.dto.TeamInput;
import com.mozip.domain.organization.entity.Invitation;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.Team;
import com.mozip.domain.organization.exception.MemberInputReason;
import com.mozip.domain.organization.exception.TeamErrorCode;
import com.mozip.domain.organization.repository.InvitationRepository;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.repository.TeamRepository;
import com.mozip.domain.posting.repository.PositionRepository;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamService {

	private static final int NAME_MAX_LENGTH = 50;

	private final TeamRepository teamRepository;
	private final MemberRepository memberRepository;
	private final InvitationRepository invitationRepository;
	private final PositionRepository positionRepository;
	private final OrganizationService organizationService;

	// 보낸 목록으로 전체 교체, id 있으면 수정하고 없으면 생성, 빠진 팀은 삭제 (순서는 배열 순서)
	@Transactional
	public List<Team> saveTeams(Long userId, Long organizationId, List<TeamInput> inputs) {
		Organization organization = organizationService.getOrganizationForUpdate(organizationId);
		if (!memberRepository.existsByOrganizationIdAndUserIdAndLeftAtIsNull(organizationId, userId)) {
			throw new BusinessException(CommonErrorCode.FORBIDDEN)
				.addContext("organizationId", organizationId)
				.addContext("userId", userId);
		}
		if (organization.isNoTeam()) {
			throw invalid(MemberInputReason.NO_TEAM_ORGANIZATION);
		}
		List<String> names = validateNames(inputs);

		Map<Long, Team> existing = teamRepository.findAllByOrganizationIdOrderBySortOrderAscIdAsc(organizationId)
			.stream()
			.collect(Collectors.toMap(Team::getId, Function.identity()));
		Set<Long> keptIds = new HashSet<>();
		for (TeamInput input : inputs) {
			if (input.id() == null) {
				continue;
			}
			if (!existing.containsKey(input.id())) {
				throw invalid(MemberInputReason.TEAM_NOT_FOUND).addContext("teamId", input.id());
			}
			if (!keptIds.add(input.id())) {
				throw invalid(MemberInputReason.TEAM_DUPLICATED).addContext("teamId", input.id());
			}
		}

		List<Team> removed = existing.values().stream().filter(team -> !keptIds.contains(team.getId())).toList();
		deleteTeams(removed);

		List<Team> saved = new ArrayList<>();
		for (int i = 0; i < inputs.size(); i++) {
			Long id = inputs.get(i).id();
			if (id == null) {
				saved.add(teamRepository.save(Team.create(organization, names.get(i), i)));
			} else {
				Team team = existing.get(id);
				team.update(names.get(i), i);
				saved.add(team);
			}
		}
		return saved;
	}

	// 공고 포지션이 쓰는 팀은 삭제 불가, 소속 운영진과 대기 중 초대는 팀 미지정으로 돌린 뒤 삭제
	private void deleteTeams(List<Team> removed) {
		if (removed.isEmpty()) {
			return;
		}
		if (positionRepository.existsByTeamIn(removed)) {
			throw new BusinessException(TeamErrorCode.TEAM_IN_USE)
				.addContext("teamIds", removed.stream().map(Team::getId).toList());
		}
		memberRepository.findAllByTeamIn(removed).forEach(Member::leaveTeam);
		invitationRepository.findAllByTeamIn(removed).forEach(Invitation::clearTeam);
		teamRepository.deleteAll(removed);
	}

	// 앞뒤 공백을 지운 이름 목록. 빈 이름, 50자 초과, 같은 이름 두 번은 거부
	private static List<String> validateNames(List<TeamInput> inputs) {
		List<String> names = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (TeamInput input : inputs) {
			String name = input.name() == null ? "" : input.name().strip();
			if (name.isEmpty() || name.length() > NAME_MAX_LENGTH) {
				throw invalid(MemberInputReason.TEAM_NAME_INVALID);
			}
			if (!seen.add(name)) {
				throw invalid(MemberInputReason.TEAM_DUPLICATED).addContext("name", name);
			}
			names.add(name);
		}
		return names;
	}

	private static BusinessException invalid(MemberInputReason reason) {
		return new BusinessException(CommonErrorCode.INVALID_INPUT, reason);
	}

}
