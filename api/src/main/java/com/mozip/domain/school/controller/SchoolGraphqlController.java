package com.mozip.domain.school.controller;

import java.util.List;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import com.mozip.domain.school.entity.School;
import com.mozip.domain.school.entity.SchoolVerification;
import com.mozip.domain.school.service.SchoolVerificationService;
import com.mozip.domain.user.dto.Me;
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

	@MutationMapping
	public boolean requestSchoolEmailVerification(@Argument String email,
		@AuthenticationPrincipal AuthUser authUser) {
		return schoolVerificationService.requestSchoolEmailVerification(authUser.userId(), email);
	}

	@MutationMapping
	public Me verifySchoolEmail(@Argument String email, @Argument String code,
		@AuthenticationPrincipal AuthUser authUser) {
		return schoolVerificationService.verifySchoolEmail(authUser.userId(), email, code);
	}

	@MutationMapping
	public SchoolVerification submitEnrollmentCertificate(@Argument Long schoolId, @Argument String fileKey,
		@AuthenticationPrincipal AuthUser authUser) {
		return schoolVerificationService.submitEnrollmentCertificate(authUser.userId(), schoolId, fileKey);
	}

}
