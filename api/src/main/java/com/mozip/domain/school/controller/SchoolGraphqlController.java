package com.mozip.domain.school.controller;

import java.util.List;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import com.mozip.domain.school.entity.School;
import com.mozip.domain.school.entity.SchoolEmailVerificationStatus;
import com.mozip.domain.school.service.SchoolVerificationService;
import com.mozip.global.security.AuthUser;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class SchoolGraphqlController {

	private final SchoolVerificationService schoolVerificationService;

	@QueryMapping
	public List<School> schools(@Argument String keyword) {
		return schoolVerificationService.search(keyword);
	}

	@QueryMapping
	public SchoolEmailVerificationStatus schoolEmailVerificationStatus(@Argument String email) {
		return schoolVerificationService.emailVerificationStatus(email);
	}

	@MutationMapping
	public boolean requestSchoolEmailVerification(@Argument String email,
		@AuthenticationPrincipal AuthUser authUser) {
		Long userId = authUser == null ? null : authUser.userId();
		return schoolVerificationService.requestSchoolEmailVerification(userId, email);
	}

	@MutationMapping
	public boolean verifySchoolEmail(@Argument String email, @Argument String code) {
		return schoolVerificationService.verifySchoolEmail(email, code);
	}

}
