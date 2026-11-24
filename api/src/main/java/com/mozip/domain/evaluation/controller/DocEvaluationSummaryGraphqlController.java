package com.mozip.domain.evaluation.controller;

import java.util.List;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import com.mozip.domain.application.entity.DocResult;
import com.mozip.domain.evaluation.dto.ConfirmDocResultPayload;
import com.mozip.domain.evaluation.dto.DocEvaluationSummaryPage;
import com.mozip.domain.evaluation.dto.DocResultTab;
import com.mozip.domain.evaluation.dto.DocSummarySort;
import com.mozip.domain.evaluation.dto.SetDocResultPayload;
import com.mozip.domain.evaluation.entity.DocEvaluation;
import com.mozip.domain.evaluation.service.DocEvaluationSummaryService;
import com.mozip.domain.evaluation.service.DocResultService;
import com.mozip.global.security.AuthUser;

import lombok.RequiredArgsConstructor;

// 서류 평가 취합 조회와 서류 결과 지정, 확정
@Controller
@RequiredArgsConstructor
public class DocEvaluationSummaryGraphqlController {

	private final DocEvaluationSummaryService docEvaluationSummaryService;
	private final DocResultService docResultService;

	@QueryMapping
	public DocEvaluationSummaryPage docEvaluationSummary(@Argument Long postingId, @Argument DocResultTab tab,
		@Argument List<Long> positionIds, @Argument String keyword, @Argument DocSummarySort sort,
		@Argument Integer page, @AuthenticationPrincipal AuthUser authUser) {
		return docEvaluationSummaryService.findSummary(authUser.userId(), postingId, tab, positionIds, keyword, sort,
			page);
	}

	@QueryMapping
	public List<DocEvaluation> docEvaluations(@Argument Long applicationId,
		@AuthenticationPrincipal AuthUser authUser) {
		return docEvaluationSummaryService.findEvaluations(authUser.userId(), applicationId);
	}

	@MutationMapping
	public SetDocResultPayload setDocResult(@Argument Long applicationId, @Argument DocResult result,
		@AuthenticationPrincipal AuthUser authUser) {
		return docResultService.setDocResult(authUser.userId(), applicationId, result);
	}

	@MutationMapping
	public ConfirmDocResultPayload confirmDocResult(@Argument Long postingId,
		@AuthenticationPrincipal AuthUser authUser) {
		return docResultService.confirmDocResult(authUser.userId(), postingId);
	}

}
