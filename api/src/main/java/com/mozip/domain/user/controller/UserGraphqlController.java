package com.mozip.domain.user.controller;

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

import com.mozip.domain.user.dto.Me;
import com.mozip.domain.user.entity.Experience;
import com.mozip.domain.user.entity.Role;
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

	@QueryMapping
	public Me me(@AuthenticationPrincipal AuthUser authUser) {
		return userService.me(authUser.userId());
	}

	@MutationMapping
	public Me switchRole(@Argument Role role, @Argument Long organizationId, @AuthenticationPrincipal AuthUser authUser) {
		return userService.switchRole(authUser.userId(), role, organizationId);
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
