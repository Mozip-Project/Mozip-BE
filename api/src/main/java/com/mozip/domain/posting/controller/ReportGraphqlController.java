package com.mozip.domain.posting.controller;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import com.mozip.domain.posting.entity.ReportReason;
import com.mozip.domain.posting.service.ReportService;
import com.mozip.global.security.AuthUser;

import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@Validated
public class ReportGraphqlController {

	private final ReportService reportService;

	@MutationMapping
	public boolean reportPosting(@Argument Long postingId, @Argument ReportReason reason,
		@Argument @Size(max = 300, message = "최대 300자까지 입력할 수 있어요") String detail,
		@AuthenticationPrincipal AuthUser authUser) {
		return reportService.report(authUser.userId(), postingId, reason, detail);
	}

}
