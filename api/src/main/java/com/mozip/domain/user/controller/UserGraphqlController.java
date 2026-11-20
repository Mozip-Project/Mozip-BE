package com.mozip.domain.user.controller;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import com.mozip.domain.user.dto.Me;
import com.mozip.domain.user.entity.Role;
import com.mozip.domain.user.service.UserService;
import com.mozip.global.security.AuthUser;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class UserGraphqlController {

	private final UserService userService;

	@QueryMapping
	public Me me(@AuthenticationPrincipal AuthUser authUser) {
		return userService.me(authUser.userId());
	}

	@MutationMapping
	public Me switchRole(@Argument Role role, @Argument Long organizationId, @AuthenticationPrincipal AuthUser authUser) {
		return userService.switchRole(authUser.userId(), role, organizationId);
	}

}
