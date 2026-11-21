package com.mozip.domain.posting.controller;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import com.mozip.domain.posting.dto.PostingPage;
import com.mozip.domain.posting.dto.ScrapResult;
import com.mozip.domain.posting.service.ScrapService;
import com.mozip.global.security.AuthUser;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class ScrapGraphqlController {

	private final ScrapService scrapService;

	@MutationMapping
	public ScrapResult toggleScrap(@Argument Long postingId, @AuthenticationPrincipal AuthUser authUser) {
		return scrapService.toggle(authUser.userId(), postingId);
	}

	@QueryMapping
	public PostingPage myScraps(@Argument int page, @Argument int size, @AuthenticationPrincipal AuthUser authUser) {
		return scrapService.findMyScraps(authUser.userId(), page, size);
	}

}
