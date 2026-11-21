package com.mozip.domain.posting.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.Team;
import com.mozip.domain.organization.repository.OrganizationRepository;
import com.mozip.domain.organization.repository.TeamRepository;
import com.mozip.domain.posting.dto.PostingDraftInput;
import com.mozip.domain.posting.dto.PostingFilter;
import com.mozip.domain.posting.dto.PostingPage;
import com.mozip.domain.posting.entity.InterviewTimeRange;
import com.mozip.domain.posting.entity.Position;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.entity.PostingImage;
import com.mozip.domain.posting.entity.PostingSort;
import com.mozip.domain.posting.entity.PostingStatus;
import com.mozip.domain.posting.entity.Question;
import com.mozip.domain.posting.repository.InterviewTimeRangeRepository;
import com.mozip.domain.posting.repository.PositionRepository;
import com.mozip.domain.posting.repository.PostingImageRepository;
import com.mozip.domain.posting.repository.PostingRepository;
import com.mozip.domain.posting.repository.QuestionRepository;
import com.mozip.domain.posting.service.PostingService;
import com.mozip.global.security.AuthUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class PostingGraphqlController {

	private final PostingService postingService;
	private final OrganizationRepository organizationRepository;
	private final PostingRepository postingRepository;
	private final PositionRepository positionRepository;
	private final QuestionRepository questionRepository;
	private final PostingImageRepository postingImageRepository;
	private final InterviewTimeRangeRepository interviewTimeRangeRepository;
	private final TeamRepository teamRepository;

	@QueryMapping
	public Posting posting(@Argument Long id, @AuthenticationPrincipal AuthUser authUser) {
		return postingService.findDetail(id, authUser == null ? null : authUser.userId());
	}

	@QueryMapping
	public PostingPage postings(@Argument PostingFilter filter, @Argument PostingSort sort, @Argument int page,
		@Argument int size) {
		return postingService.findList(filter, sort, page, size);
	}

	@QueryMapping
	public List<Posting> organizationPostings(@Argument Long organizationId, @Argument List<PostingStatus> status,
		@AuthenticationPrincipal AuthUser authUser) {
		return postingService.findOrganizationPostings(authUser.userId(), organizationId, status);
	}

	@MutationMapping
	public Posting savePostingDraft(@Argument Long organizationId, @Argument Long id,
		@Argument @Valid PostingDraftInput input, @AuthenticationPrincipal AuthUser authUser) {
		return postingService.saveDraft(authUser.userId(), organizationId, id, input);
	}

	@MutationMapping
	public Posting publishPosting(@Argument Long id, @AuthenticationPrincipal AuthUser authUser) {
		return postingService.publish(authUser.userId(), id);
	}

	@BatchMapping(typeName = "Posting", field = "organization")
	public Map<Posting, Organization> organization(List<Posting> postings) {
		List<Long> organizationIds = postings.stream()
			.map(posting -> posting.getOrganization().getId())
			.distinct()
			.toList();
		Map<Long, Organization> organizationsById = organizationRepository.findAllById(organizationIds).stream()
			.collect(Collectors.toMap(Organization::getId, Function.identity()));
		return postings.stream()
			.collect(Collectors.toMap(Function.identity(),
				posting -> organizationsById.get(posting.getOrganization().getId())));
	}

	@BatchMapping(typeName = "Posting", field = "positions")
	public Map<Posting, List<Position>> positions(List<Posting> postings) {
		return groupByPosting(postings, positionRepository.findAllByPostingInOrderBySortOrder(postings),
			Position::getPosting);
	}

	@BatchMapping(typeName = "Posting", field = "questions")
	public Map<Posting, List<Question>> questions(List<Posting> postings) {
		return groupByPosting(postings, questionRepository.findAllByPostingInOrderBySortOrder(postings),
			Question::getPosting);
	}

	@BatchMapping(typeName = "Posting", field = "images")
	public Map<Posting, List<PostingImage>> images(List<Posting> postings) {
		return groupByPosting(postings, postingImageRepository.findAllByPostingInOrderBySortOrder(postings),
			PostingImage::getPosting);
	}

	@BatchMapping(typeName = "Posting", field = "interviewTimeRanges")
	public Map<Posting, List<InterviewTimeRange>> interviewTimeRanges(List<Posting> postings) {
		return groupByPosting(postings,
			interviewTimeRangeRepository.findAllByPostingInOrderByDateAscStartTimeAsc(postings),
			InterviewTimeRange::getPosting);
	}

	// 이전 기수 불러오기로 만든 공고의 원본, null이 대부분이라 batch로 안 묶으면 매 posting마다 lazy load
	@BatchMapping(typeName = "Posting", field = "sourcePosting")
	public Map<Posting, Posting> sourcePosting(List<Posting> postings) {
		return groupToOne(postings, Posting::getSourcePosting, Posting::getId, postingRepository::findAllById);
	}

	@BatchMapping(typeName = "Position", field = "team")
	public Map<Position, Team> team(List<Position> positions) {
		return groupToOne(positions, Position::getTeam, Team::getId, teamRepository::findAllById);
	}

	@BatchMapping(typeName = "Question", field = "position")
	public Map<Question, Position> questionPosition(List<Question> questions) {
		return groupToOne(questions, Question::getPosition, Position::getId, positionRepository::findAllById);
	}

	@BatchMapping(typeName = "InterviewTimeRange", field = "position")
	public Map<InterviewTimeRange, Position> interviewTimeRangePosition(List<InterviewTimeRange> ranges) {
		return groupToOne(ranges, InterviewTimeRange::getPosition, Position::getId, positionRepository::findAllById);
	}

	// 조회된 자식의 posting은 프록시일 수 있기 때문에 id로 묶어서, 결과 Map의 키는 GraphQL이 넘긴 부모 객체 그대로 사용
	private <T> Map<Posting, List<T>> groupByPosting(List<Posting> postings, List<T> children,
		Function<T, Posting> parentGetter) {
		Map<Long, List<T>> childrenByPostingId = children.stream()
			.collect(Collectors.groupingBy(child -> parentGetter.apply(child).getId()));
		return postings.stream()
			.collect(Collectors.toMap(Function.identity(),
				posting -> childrenByPostingId.getOrDefault(posting.getId(), List.of())));
	}

	// null = 비공개/공통인 to-one 연관이라 Collectors.toMap 대신 직접 채워 넣음 (toMap은 값이 null이면 NPE)
	private <T, R> Map<T, R> groupToOne(List<T> items, Function<T, R> refGetter, Function<R, Long> idGetter,
		Function<List<Long>, List<R>> fetcher) {
		List<Long> ids = items.stream()
			.map(refGetter)
			.filter(Objects::nonNull)
			.map(idGetter)
			.distinct()
			.toList();
		Map<Long, R> byId = fetcher.apply(ids).stream()
			.collect(Collectors.toMap(idGetter, Function.identity()));
		Map<T, R> result = new HashMap<>();
		for (T item : items) {
			R ref = refGetter.apply(item);
			result.put(item, ref == null ? null : byId.get(idGetter.apply(ref)));
		}
		return result;
	}

}
