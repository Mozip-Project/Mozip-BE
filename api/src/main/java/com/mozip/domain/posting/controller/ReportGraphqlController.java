package com.mozip.domain.posting.controller;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import com.mozip.domain.posting.entity.ReportReason;
import com.mozip.domain.posting.service.ReportService;
import com.mozip.global.security.AuthUser;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class ReportGraphqlController {

	private final ReportService reportService;

	@MutationMapping
	public boolean reportPosting(@Argument Long postingId, @Argument ReportReason reason, @Argument String detail,
		@AuthenticationPrincipal AuthUser authUser) {
		return reportService.report(authUser.userId(), postingId, reason, detail);
	}

}
