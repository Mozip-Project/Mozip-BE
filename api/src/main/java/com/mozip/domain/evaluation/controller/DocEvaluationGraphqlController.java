package com.mozip.domain.evaluation.controller;

import java.util.HashMap;
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
import com.mozip.domain.application.repository.ApplicationRepository;
import com.mozip.domain.evaluation.dto.ApplicationSort;
import com.mozip.domain.evaluation.dto.DocEvaluationApplicationPage;
import com.mozip.domain.evaluation.dto.DocEvaluationApplicationRow;
import com.mozip.domain.evaluation.dto.DocEvaluationTab;
import com.mozip.domain.evaluation.dto.SaveDocEvaluationInput;
import com.mozip.domain.evaluation.dto.SaveDocEvaluationPayload;
import com.mozip.domain.evaluation.entity.DocEvaluation;
import com.mozip.domain.evaluation.entity.DocScore;
import com.mozip.domain.evaluation.service.DocEvaluationService;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.posting.entity.Question;
import com.mozip.domain.posting.repository.QuestionRepository;
import com.mozip.global.security.AuthUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class DocEvaluationGraphqlController {

	private final DocEvaluationService docEvaluationService;
	private final ApplicationRepository applicationRepository;
	private final MemberRepository memberRepository;
	private final QuestionRepository questionRepository;

	@QueryMapping
	public DocEvaluationApplicationPage docEvaluationApplications(@Argument Long postingId,
		@Argument DocEvaluationTab tab, @Argument List<Long> positionIds, @Argument String keyword,
		@Argument ApplicationSort sort, @Argument Integer page, @AuthenticationPrincipal AuthUser authUser) {
		return docEvaluationService.findApplications(authUser.userId(), postingId, tab, positionIds, keyword, sort,
			page);
	}

	@QueryMapping
	public List<DocEvaluationApplicationRow> docEvaluationApplicationChips(@Argument Long postingId,
		@Argument DocEvaluationTab tab, @Argument List<Long> positionIds, @Argument String keyword,
		@Argument ApplicationSort sort, @AuthenticationPrincipal AuthUser authUser) {
		return docEvaluationService.findApplicationChips(authUser.userId(), postingId, tab, positionIds, keyword,
			sort);
	}

	@QueryMapping
	public DocEvaluation myDocEvaluation(@Argument Long applicationId, @AuthenticationPrincipal AuthUser authUser) {
		return docEvaluationService.findMyEvaluation(authUser.userId(), applicationId);
	}

	@MutationMapping
	public SaveDocEvaluationPayload saveDocEvaluation(@Argument @Valid SaveDocEvaluationInput input,
		@Argument boolean submit, @AuthenticationPrincipal AuthUser authUser) {
		return docEvaluationService.save(authUser.userId(), input, submit);
	}

	@BatchMapping(typeName = "DocEvaluation", field = "application")
	public Map<DocEvaluation, Application> application(List<DocEvaluation> evaluations) {
		return attach(evaluations, evaluation -> evaluation.getApplication().getId(), Application::getId,
			applicationRepository::findAllById);
	}

	// Member의 하위 필드(user, organization, team)는 getter로 읽으므로 한 번에 같이 조회해 둠
	@BatchMapping(typeName = "DocEvaluation", field = "member")
	public Map<DocEvaluation, Member> member(List<DocEvaluation> evaluations) {
		return attach(evaluations, evaluation -> evaluation.getMember().getId(), Member::getId,
			memberRepository::findAllWithUserAndTeamByIdIn);
	}

	// 점수는 jsonb에 문항 id만 들어 있어 문항을 한 번에 조회해 붙임
	@BatchMapping(typeName = "DocScore", field = "question")
	public Map<DocScore, Question> question(List<DocScore> scores) {
		return attach(scores, DocScore::questionId, Question::getId, questionRepository::findAllById);
	}

	// 부모가 가진 id로 대상을 한 번에 조회해 붙인다. 결과 Map의 키는 GraphQL이 넘긴 부모 객체 그대로 사용
	private static <T, R> Map<T, R> attach(List<T> parents, Function<T, Long> targetIdGetter,
		Function<R, Long> idGetter, Function<List<Long>, List<R>> fetcher) {
		List<Long> ids = parents.stream().map(targetIdGetter).distinct().toList();
		Map<Long, R> byId = fetcher.apply(ids).stream().collect(Collectors.toMap(idGetter, Function.identity()));
		Map<T, R> result = new HashMap<>();
		for (T parent : parents) {
			result.put(parent, byId.get(targetIdGetter.apply(parent)));
		}
		return result;
	}

}
