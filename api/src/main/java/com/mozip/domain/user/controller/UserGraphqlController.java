package com.mozip.domain.user.controller;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.user.dto.Me;
import com.mozip.domain.user.entity.Experience;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.ExperienceRepository;
import com.mozip.domain.user.service.UserService;
import com.mozip.global.security.AuthUser;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class UserGraphqlController {

	private final UserService userService;
	private final ExperienceRepository experienceRepository;
	private final MemberRepository memberRepository;

	@QueryMapping
	public Me me(@AuthenticationPrincipal AuthUser authUser) {
		return userService.me(authUser.userId());
	}

	// 소속 단체 드롭다운 항목, 단체와 팀을 함께 조회
	@SchemaMapping(typeName = "Me")
	public List<Member> memberships(Me me) {
		return memberRepository.findAllWithOrganizationAndTeamByUserId(me.id());
	}

	// User를 여러 명 조회할 때(예: organizations.members.user.experiences) N+1을 막기 위한 배치 조회
	@BatchMapping(typeName = "User", field = "experiences")
	public Map<User, List<Experience>> experiences(List<User> users) {
		List<Long> userIds = users.stream().map(User::getId).toList();

		Map<Long, List<Experience>> byUserId = experienceRepository.findByUserIdIn(userIds)
			.stream()
			.collect(Collectors.groupingBy(experience -> experience.getUser().getId()));

		return users.stream()
			.collect(Collectors.toMap(Function.identity(), user -> byUserId.getOrDefault(user.getId(), List.of())));
	}

}
