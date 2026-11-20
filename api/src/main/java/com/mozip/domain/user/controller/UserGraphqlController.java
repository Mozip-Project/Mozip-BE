package com.mozip.domain.user.controller;

import java.util.List;

import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.service.UserService;
import com.mozip.global.security.AuthUser;

import lombok.RequiredArgsConstructor;

// GraphQL Me 타입은 User 엔티티로 그림 (id, name, lastRole, lastOrganization은 getter로 응답)
@Controller
@RequiredArgsConstructor
public class UserGraphqlController {

	private final UserService userService;
	private final MemberRepository memberRepository;

	@QueryMapping
	public User me(@AuthenticationPrincipal AuthUser authUser) {
		return userService.getMe(authUser.userId());
	}

	// me 한 번에 memberships 한 번이라 배치 조회가 필요 없음
	@SchemaMapping(typeName = "Me")
	public List<Member> memberships(User user) {
		return memberRepository.findAllWithOrganizationAndTeamByUserId(user.getId());
	}

}
