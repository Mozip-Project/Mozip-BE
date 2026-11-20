package com.mozip.domain.organization.controller;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import com.mozip.domain.organization.dto.UpdateOrganizationLogoInput;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.service.OrganizationService;
import com.mozip.global.security.AuthUser;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class OrganizationGraphqlController {

	private final OrganizationService organizationService;

	@MutationMapping
	public Organization updateOrganizationLogo(@Argument UpdateOrganizationLogoInput input,
		@AuthenticationPrincipal AuthUser authUser) {
		return organizationService.updateLogo(input.organizationId(), input.fileKey(), authUser.userId());
	}

}
