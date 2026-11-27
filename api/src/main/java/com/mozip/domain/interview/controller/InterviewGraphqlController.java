package com.mozip.domain.interview.controller;

import java.time.LocalDateTime;
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

import com.mozip.domain.application.entity.Application;
import com.mozip.domain.interview.dto.InterviewTimetable;
import com.mozip.domain.interview.entity.Interview;
import com.mozip.domain.interview.entity.InterviewApplicant;
import com.mozip.domain.interview.entity.Interviewer;
import com.mozip.domain.interview.repository.InterviewApplicantRepository;
import com.mozip.domain.interview.repository.InterviewerRepository;
import com.mozip.domain.interview.service.InterviewAvailabilityService;
import com.mozip.domain.interview.service.InterviewTimetableService;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.repository.PostingRepository;
import com.mozip.global.security.AuthUser;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class InterviewGraphqlController {

	private final InterviewAvailabilityService interviewAvailabilityService;
	private final InterviewTimetableService interviewTimetableService;
	private final InterviewApplicantRepository interviewApplicantRepository;
	private final InterviewerRepository interviewerRepository;
	private final PostingRepository postingRepository;

	@MutationMapping
	public Application selectInterviewTimes(@Argument Long applicationId,
		@Argument List<LocalDateTime> availableTimes, @AuthenticationPrincipal AuthUser authUser) {
		return interviewAvailabilityService.reselect(authUser.userId(), applicationId, availableTimes);
	}

	@QueryMapping
	public InterviewTimetable interviewTimetable(@Argument Long postingId,
		@AuthenticationPrincipal AuthUser authUser) {
		return interviewTimetableService.find(authUser.userId(), postingId);
	}

	@MutationMapping
	public InterviewTimetable generateInterviewTimetable(@Argument Long postingId,
		@AuthenticationPrincipal AuthUser authUser) {
		return interviewTimetableService.generate(authUser.userId(), postingId);
	}

	@BatchMapping(typeName = "Interview", field = "posting")
	public Map<Interview, Posting> posting(List<Interview> interviews) {
		List<Long> postingIds = interviews.stream().map(interview -> interview.getPosting().getId()).distinct().toList();
		Map<Long, Posting> postingById = postingRepository.findAllById(postingIds).stream()
			.collect(Collectors.toMap(Posting::getId, Function.identity()));
		return interviews.stream()
			.collect(Collectors.toMap(Function.identity(),
				interview -> postingById.get(interview.getPosting().getId())));
	}

	@BatchMapping(typeName = "Interview")
	public Map<Interview, List<Application>> applicants(List<Interview> interviews) {
		return groupByInterview(interviews, interviewApplicantRepository.findAllWithApplicationByInterviewIn(interviews),
			applicant -> applicant.getInterview().getId(), InterviewApplicant::getApplication);
	}

	@BatchMapping(typeName = "Interview")
	public Map<Interview, List<Member>> interviewers(List<Interview> interviews) {
		return groupByInterview(interviews, interviewerRepository.findAllWithMemberByInterviewIn(interviews),
			interviewer -> interviewer.getInterview().getId(), Interviewer::getMember);
	}

	// 자식의 면접 참조는 프록시일 수 있어 id로 묶고, 결과 Map의 키는 GraphQL이 넘긴 Interview 그대로 사용
	private <C, R> Map<Interview, List<R>> groupByInterview(List<Interview> interviews, List<C> children,
		Function<C, Long> interviewIdGetter, Function<C, R> mapper) {
		Map<Long, List<R>> byInterviewId = children.stream()
			.collect(Collectors.groupingBy(interviewIdGetter, Collectors.mapping(mapper, Collectors.toList())));
		return interviews.stream()
			.collect(Collectors.toMap(Function.identity(),
				interview -> byInterviewId.getOrDefault(interview.getId(), List.of())));
	}

}
