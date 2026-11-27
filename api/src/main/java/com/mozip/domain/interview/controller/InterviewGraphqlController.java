package com.mozip.domain.interview.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import com.mozip.domain.application.entity.Application;
import com.mozip.domain.interview.service.InterviewAvailabilityService;
import com.mozip.global.security.AuthUser;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class InterviewGraphqlController {

	private final InterviewAvailabilityService interviewAvailabilityService;

	@MutationMapping
	public Application selectInterviewTimes(@Argument Long applicationId,
		@Argument List<LocalDateTime> availableTimes, @AuthenticationPrincipal AuthUser authUser) {
		return interviewAvailabilityService.reselect(authUser.userId(), applicationId, availableTimes);
	}

}
