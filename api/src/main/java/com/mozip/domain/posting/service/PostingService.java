package com.mozip.domain.posting.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.graphql.data.ArgumentValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.domain.evaluation.service.PostingResultChecker;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.Team;
import com.mozip.domain.organization.exception.OrganizationErrorCode;
import com.mozip.domain.organization.repository.TeamRepository;
import com.mozip.domain.organization.service.OrganizationAccessChecker;
import com.mozip.domain.payment.service.SubscriptionService;
import com.mozip.domain.posting.dto.InterviewTimeRangeInput;
import com.mozip.domain.posting.dto.PositionInput;
import com.mozip.domain.posting.dto.PostingDraftInput;
import com.mozip.domain.posting.dto.PostingFilter;
import com.mozip.domain.posting.dto.PostingImageInput;
import com.mozip.domain.posting.dto.PostingPage;
import com.mozip.domain.posting.dto.QuestionInput;
import com.mozip.domain.posting.entity.InterviewTimeRange;
import com.mozip.domain.posting.entity.Position;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.entity.PostingImage;
import com.mozip.domain.posting.entity.PostingSort;
import com.mozip.domain.posting.entity.PostingStatus;
import com.mozip.domain.posting.entity.Question;
import com.mozip.domain.posting.exception.PostingConflictReason;
import com.mozip.domain.posting.exception.PostingErrorCode;
import com.mozip.domain.posting.repository.InterviewTimeRangeRepository;
import com.mozip.domain.posting.repository.PositionRepository;
import com.mozip.domain.posting.repository.PostingImageRepository;
import com.mozip.domain.posting.repository.PostingRepository;
import com.mozip.domain.posting.repository.PostingSpecifications;
import com.mozip.domain.posting.repository.QuestionRepository;
import com.mozip.global.common.response.FieldErrorResponse;
import com.mozip.global.config.graphql.LocalDateTimeScalar;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostingService {

	private static final int MAX_IMAGES = 5;
	private static final int MAX_PAGE_SIZE = 50;

	private final PostingRepository postingRepository;
	private final PositionRepository positionRepository;
	private final QuestionRepository questionRepository;
	private final PostingImageRepository postingImageRepository;
	private final InterviewTimeRangeRepository interviewTimeRangeRepository;
	private final TeamRepository teamRepository;
	private final OrganizationAccessChecker organizationAccessChecker;
	private final SubscriptionService subscriptionService;
	private final PostingResultChecker postingResultChecker;

	@Transactional
	public Posting saveDraft(Long userId, Long organizationId, Long postingId, PostingDraftInput input) {
		requireOperator(userId, organizationId);

		Posting posting;
		if (postingId == null) {
			posting = createDraft(organizationId, input);
		} else {
			posting = findById(postingId);
			// 다른 단체 소속 공고를 id만 맞혀서 수정/조회하지 못하게 막음. 403이 아니라 404인 이유는 findDetail과 같음
			// (403을 주면 id를 바꿔가며 불러서 남의 DRAFT가 있는지 알아낼 수 있음)
			if (!posting.getOrganization().getId().equals(organizationId)) {
				throw new BusinessException(PostingErrorCode.NOT_FOUND).addContext("postingId", postingId);
			}
			if (!posting.isDraft()) {
				throw new BusinessException(PostingErrorCode.CONFLICT, PostingConflictReason.NOT_DRAFT)
					.addContext("postingId", postingId);
			}
		}

		applyBasicStep(posting, input);
		applyPositionsStep(posting, input);
		applyInterviewStep(posting, input);
		applyQuestionsStep(posting, input);

		return posting;
	}

	@Transactional
	public Posting publish(Long userId, Long postingId) {
		Posting posting = findById(postingId);
		Long organizationId = posting.getOrganization().getId();
		requireOperator(userId, organizationId);

		if (!posting.isDraft()) {
			throw new BusinessException(PostingErrorCode.CONFLICT, PostingConflictReason.ALREADY_PUBLISHED)
				.addContext("postingId", postingId);
		}
		if (!subscriptionService.hasActivePlan(organizationId)) {
			throw new BusinessException(OrganizationErrorCode.SUBSCRIPTION_REQUIRED)
				.addContext("organizationId", organizationId);
		}
		validateRequiredSteps(posting);

		posting.publishNow();
		return posting;
	}

	@Transactional
	public Posting advanceStatus(Long userId, Long postingId, PostingStatus to) {
		Posting posting = findById(postingId);
		requireOperator(userId, posting.getOrganization().getId());

		PostingStatus next = nextStatus(posting);
		if (next == null || next != to) {
			throw new BusinessException(PostingErrorCode.CONFLICT, PostingConflictReason.INVALID_STATUS_TRANSITION)
				.addContext("postingId", postingId)
				.addContext("from", posting.getStatus())
				.addContext("to", to);
		}
		if (to == PostingStatus.DOC_RESULT_NOTICE && !postingResultChecker.isDocResultConfirmed(postingId)) {
			throw new BusinessException(PostingErrorCode.CONFLICT, PostingConflictReason.DOC_RESULT_NOT_CONFIRMED)
				.addContext("postingId", postingId);
		}
		if (to == PostingStatus.FINAL_RESULT && !postingResultChecker.isFinalResultConfirmed(postingId)) {
			throw new BusinessException(PostingErrorCode.CONFLICT, PostingConflictReason.FINAL_RESULT_NOT_CONFIRMED)
				.addContext("postingId", postingId);
		}

		posting.advanceStatus(to);
		return posting;
	}

	@Transactional
	public void delete(Long userId, Long postingId) {
		Posting posting = findById(postingId);
		requireOperator(userId, posting.getOrganization().getId());
		if (!posting.isDraft()) {
			throw new BusinessException(PostingErrorCode.CONFLICT, PostingConflictReason.NOT_DRAFT)
				.addContext("postingId", postingId);
		}

		questionRepository.deleteAllByPosting(posting);
		interviewTimeRangeRepository.deleteAllByPosting(posting);
		positionRepository.deleteAllByPosting(posting);
		postingImageRepository.deleteAllByPosting(posting);
		postingRepository.delete(posting);
	}

	// B가 평가/면접 화면을 열어도 되는 단계인지 확인할 때 쓰는 연결 함수 (A -> B, ab-boundary.md)
	public PostingStatus getPostingStatus(Long postingId) {
		return findById(postingId).getStatus();
	}

	// advancePostingStatus에서 허용하는 단 하나의 다음 단계. 없으면(DRAFT, ENDED) null
	private static PostingStatus nextStatus(Posting posting) {
		return switch (posting.getStatus()) {
			case RECRUITING -> PostingStatus.DOC_REVIEW;
			case DOC_REVIEW -> PostingStatus.DOC_RESULT_NOTICE;
			case DOC_RESULT_NOTICE -> posting.isHasInterview() ? PostingStatus.INTERVIEW : PostingStatus.FINAL_RESULT;
			case INTERVIEW -> PostingStatus.FINAL_RESULT;
			case FINAL_RESULT -> PostingStatus.ENDED;
			case DRAFT, ENDED -> null;
		};
	}

	public Posting findDetail(Long postingId, Long requestUserId) {
		Posting posting = postingRepository.findByIdWithOrganization(postingId)
			.orElseThrow(() -> new BusinessException(PostingErrorCode.NOT_FOUND).addContext("postingId", postingId));
		if (posting.isDraft()) {
			// DRAFT는 그 단체 운영진만 볼 수 있음. 비로그인/다른 회원에게는 있는지조차 안 알려줌
			if (requestUserId == null
				|| !organizationAccessChecker.isOperatorOf(requestUserId, posting.getOrganization().getId())) {
				throw new BusinessException(PostingErrorCode.NOT_FOUND).addContext("postingId", postingId);
			}
		}
		return posting;
	}

	public PostingPage findList(PostingFilter filter, PostingSort sort, int page, int size) {
		if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT)
				.addContext("page", page)
				.addContext("size", size);
		}
		Specification<Posting> spec = Specification.where(PostingSpecifications.statusIn(PostingStatus.RECRUITING));
		if (filter != null) {
			spec = addIfPresent(spec, PostingSpecifications.keyword(filter.keyword()));
			spec = addIfPresent(spec, PostingSpecifications.schoolId(filter.schoolId()));
			spec = addIfPresent(spec, PostingSpecifications.organizationType(filter.organizationType()));
			spec = addIfPresent(spec, PostingSpecifications.hasInterview(filter.hasInterview()));
			spec = addIfPresent(spec,
				PostingSpecifications.closingSoon(filter.closingSoon(), LocalDateTime.now(LocalDateTimeScalar.ZONE)));
		}

		PostingSort effectiveSort = sort != null ? sort : PostingSort.LATEST;
		Pageable pageable = PageRequest.of(page, size, effectiveSort.toJpaSort());

		Page<Posting> result = postingRepository.findAll(spec, pageable);
		return new PostingPage(result.getContent(), result.getTotalElements(), result.hasNext());
	}

	public List<Posting> findOrganizationPostings(Long userId, Long organizationId, List<PostingStatus> statuses) {
		requireOperator(userId, organizationId);
		Organization organization = organizationAccessChecker.getOrganization(organizationId);
		List<PostingStatus> targetStatuses = statuses != null && !statuses.isEmpty()
			? statuses
			: List.of(PostingStatus.values());
		return postingRepository.findAllByOrganizationAndStatusInOrderByCreatedAtDesc(organization, targetStatuses);
	}

	private Posting createDraft(Long organizationId, PostingDraftInput input) {
		if (input.title() == null || input.title().isBlank()) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT)
				.addContext("reason", "새 공고는 title이 있어야 합니다");
		}
		Organization organization = organizationAccessChecker.getOrganization(organizationId);
		return postingRepository.save(Posting.create(organization, input.title()));
	}

	private void applyBasicStep(Posting posting, PostingDraftInput input) {
		if (!touchesBasicStep(input)) {
			return;
		}
		String semester = resolve(input.semester(), posting.getSemester());
		String region = resolve(input.region(), posting.getRegion());
		String fee = resolve(input.fee(), posting.getFee());
		String eligibilityNote = resolve(input.eligibilityNote(), posting.getEligibilityNote());
		validateOptionalBasicFieldLengths(semester, region, fee, eligibilityNote);
		posting.updateBasicStep(
			orElse(input.title(), posting.getTitle()),
			orElse(input.content(), posting.getContent()),
			semester,
			region,
			fee,
			orElse(input.allowEnrolled(), posting.isAllowEnrolled()),
			orElse(input.allowOnLeave(), posting.isAllowOnLeave()),
			eligibilityNote,
			resolve(input.activityStartedOn(), posting.getActivityStartedOn()),
			resolve(input.activityEndedOn(), posting.getActivityEndedOn()),
			orElse(input.recruitStartAt(), posting.getRecruitStartAt()),
			orElse(input.recruitEndAt(), posting.getRecruitEndAt()));
		if (input.images() != null) {
			replaceImages(posting, input.images());
		}
	}

	// semester/region/fee/eligibilityNote는 지울 수 있어야 해서 ArgumentValue로 받다 보니 @Size가 안 걸림.
	// null(지움)은 통과시키고 실제 값이 있을 때만 길이를 봄
	private void validateOptionalBasicFieldLengths(String semester, String region, String fee,
		String eligibilityNote) {
		List<FieldErrorResponse> fieldErrors = new ArrayList<>();
		if (semester != null && semester.length() > 20) {
			fieldErrors.add(new FieldErrorResponse("input.semester", "학기는 20자까지 입력할 수 있어요"));
		}
		if (region != null && region.length() > 100) {
			fieldErrors.add(new FieldErrorResponse("input.region", "지역은 100자까지 입력할 수 있어요"));
		}
		if (fee != null && fee.length() > 50) {
			fieldErrors.add(new FieldErrorResponse("input.fee", "활동비는 50자까지 입력할 수 있어요"));
		}
		if (eligibilityNote != null && eligibilityNote.length() > 200) {
			fieldErrors.add(new FieldErrorResponse("input.eligibilityNote", "지원 자격은 200자까지 입력할 수 있어요"));
		}
		if (!fieldErrors.isEmpty()) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT).withFieldErrors(fieldErrors);
		}
	}

	private void applyPositionsStep(Posting posting, PostingDraftInput input) {
		if (!touchesPositionsStep(input)) {
			return;
		}
		boolean noPosition = orElse(input.noPosition(), posting.isNoPosition());
		posting.updatePositionsStep(noPosition);
		if (input.positions() != null) {
			replacePositions(posting, input.positions());
		} else if (noPosition) {
			// 포지션 없음으로 바꾸면서 positions를 안 보내도 이전에 저장된 포지션은 지워야 함
			replacePositions(posting, List.of());
		}
	}

	private void applyInterviewStep(Posting posting, PostingDraftInput input) {
		if (!touchesInterviewStep(input)) {
			return;
		}
		boolean hasInterview = orElse(input.hasInterview(), posting.isHasInterview());
		posting.updateInterviewStep(
			hasInterview,
			orElse(input.interviewMethod(), posting.getInterviewMethod()),
			orElse(input.interviewMinutes(), posting.getInterviewMinutes()),
			orElse(input.breakMinutes(), posting.getBreakMinutes()),
			orElse(input.assignRule(), posting.getAssignRule()),
			orElse(input.maxApplicantsPerInterview(), posting.getMaxApplicantsPerInterview()),
			orElse(input.maxParallelInterviews(), posting.getMaxParallelInterviews()));
		if (input.interviewTimeRanges() != null) {
			replaceInterviewTimeRanges(posting, input.interviewTimeRanges());
		} else if (!hasInterview) {
			// 면접 없음으로 바뀌면서 interviewTimeRanges를 안 보내도 이전에 저장된 시간대는 지워야 함
			replaceInterviewTimeRanges(posting, List.of());
		}
	}

	private void applyQuestionsStep(Posting posting, PostingDraftInput input) {
		if (!touchesQuestionsStep(input)) {
			return;
		}
		posting.updateQuestionsStep(orElse(input.docScorePerQuestion(), Boolean.TRUE.equals(posting.getDocScorePerQuestion())));
		if (input.questions() != null) {
			replaceQuestions(posting, input.questions());
		}
	}

	private void replaceImages(Posting posting, List<PostingImageInput> imageInputs) {
		if (imageInputs.size() > MAX_IMAGES) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT)
				.addContext("imageCount", imageInputs.size());
		}
		// 대표 지정은 이미지 목록과 같은 요청으로 들어오므로(isRepresentative), id로 따로 가리킬 때 생기던
		// "다시 만들어서 id가 바뀐 이미지를 가리킨다" 문제가 없음. 여러 장에 true를 보내면 어느 것인지 알 수 없어 거부
		long representativeCount = imageInputs.stream().filter(PostingImageInput::isRepresentative).count();
		if (representativeCount > 1) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT)
				.addContext("reason", "대표 이미지는 한 장만 고를 수 있어요");
		}
		postingImageRepository.deleteAllByPosting(posting);
		List<PostingImage> images = imageInputs.stream()
			.map(i -> PostingImage.create(posting, i.url(), i.sortOrder(), i.isRepresentative()))
			.toList();
		postingImageRepository.saveAll(images);
	}

	private void replacePositions(Posting posting, List<PositionInput> positionInputs) {
		positionRepository.deleteAllByPosting(posting);
		Set<Long> teamIds = positionInputs.stream()
			.map(PositionInput::teamId)
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());
		Map<Long, Team> teams = resolveTeams(posting, teamIds);
		List<Position> positions = positionInputs.stream()
			.map(i -> Position.create(posting, i.teamId() == null ? null : teams.get(i.teamId()),
				i.name(), i.headcount(), i.sortOrder()))
			.toList();
		positionRepository.saveAll(positions);
	}

	private void replaceQuestions(Posting posting, List<QuestionInput> questionInputs) {
		questionRepository.deleteAllByPosting(posting);
		Set<Long> positionIds = questionInputs.stream()
			.map(QuestionInput::positionId)
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());
		Map<Long, Position> positions = resolvePositions(posting, positionIds);
		List<Question> questions = new ArrayList<>();
		for (QuestionInput i : questionInputs) {
			Position position = i.positionId() == null ? null : positions.get(i.positionId());
			List<String> choices = i.options() != null ? i.options().choices() : null;
			List<String> columns = i.options() != null ? i.options().columns() : null;
			Integer maxRows = i.options() != null ? i.options().maxRows() : null;
			Integer maxFiles = i.options() != null ? i.options().maxFiles() : null;
			questions.add(Question.create(posting, position, i.type(), i.title(), i.description(), i.required(),
				i.maxLength(), choices, columns, maxRows, maxFiles, i.sortOrder()));
		}
		questionRepository.saveAll(questions);
	}

	private void replaceInterviewTimeRanges(Posting posting, List<InterviewTimeRangeInput> rangeInputs) {
		interviewTimeRangeRepository.deleteAllByPosting(posting);
		validateInterviewTimeRanges(rangeInputs);
		Set<Long> positionIds = rangeInputs.stream()
			.map(InterviewTimeRangeInput::positionId)
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());
		Map<Long, Position> positions = resolvePositions(posting, positionIds);
		List<InterviewTimeRange> ranges = new ArrayList<>();
		for (InterviewTimeRangeInput i : rangeInputs) {
			Position position = i.positionId() == null ? null : positions.get(i.positionId());
			List<LocalTime> closedSlots = i.closedSlots() == null ? List.of() : i.closedSlots();
			ranges.add(InterviewTimeRange.create(posting, position, i.date(), i.startTime(), i.endTime(),
				closedSlots));
		}
		interviewTimeRangeRepository.saveAll(ranges);
	}

	// startTime < endTime, 같은 날짜 안에서 구간이 겹치면 COMMON_400. 겹친 구간이 저장되면 B의 타임테이블 계산에서
	// 같은 시각의 면접 칸이 두 번 생김(ab-boundary.md). 동시 진행은 maxParallelInterviews로 표현하므로 구간 자체는 겹칠 이유가 없음
	private void validateInterviewTimeRanges(List<InterviewTimeRangeInput> rangeInputs) {
		for (InterviewTimeRangeInput i : rangeInputs) {
			if (!i.startTime().isBefore(i.endTime())) {
				throw new BusinessException(CommonErrorCode.INVALID_INPUT).addContext("date", i.date());
			}
		}
		Map<LocalDate, List<InterviewTimeRangeInput>> byDate = rangeInputs.stream()
			.collect(Collectors.groupingBy(InterviewTimeRangeInput::date));
		for (List<InterviewTimeRangeInput> sameDate : byDate.values()) {
			List<InterviewTimeRangeInput> sorted = sameDate.stream()
				.sorted(Comparator.comparing(InterviewTimeRangeInput::startTime))
				.toList();
			for (int i = 1; i < sorted.size(); i++) {
				if (sorted.get(i).startTime().isBefore(sorted.get(i - 1).endTime())) {
					throw new BusinessException(CommonErrorCode.INVALID_INPUT).addContext("date", sorted.get(i).date());
				}
			}
		}
	}

	// teamId가 이 공고의 단체 소속인지 한 번에 확인. 다른 단체 팀 id나 없는 id면 COMMON_400
	private Map<Long, Team> resolveTeams(Posting posting, Set<Long> teamIds) {
		if (teamIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, Team> teams = teamRepository.findAllById(teamIds).stream()
			.filter(team -> team.getOrganization().getId().equals(posting.getOrganization().getId()))
			.collect(Collectors.toMap(Team::getId, Function.identity()));
		if (teams.size() != teamIds.size()) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT).addContext("teamIds", teamIds);
		}
		return teams;
	}

	// positionId가 이 공고에 2단계로 먼저 저장된 포지션인지 한 번에 확인. 다른 공고 포지션 id나 없는 id면 COMMON_400
	private Map<Long, Position> resolvePositions(Posting posting, Set<Long> positionIds) {
		if (positionIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, Position> positions = positionRepository.findAllById(positionIds).stream()
			.filter(position -> position.getPosting().getId().equals(posting.getId()))
			.collect(Collectors.toMap(Position::getId, Function.identity()));
		if (positions.size() != positionIds.size()) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT).addContext("positionIds", positionIds);
		}
		return positions;
	}

	// 필수 단계 누락 검사. 문제 있는 단계를 fieldErrors에 전부 모아서 한 번에 알림
	private void validateRequiredSteps(Posting posting) {
		List<FieldErrorResponse> fieldErrors = new ArrayList<>();
		if (posting.getTitle() == null || posting.getTitle().isBlank() || posting.getContent() == null) {
			fieldErrors.add(new FieldErrorResponse("basicStep", "기본 정보(제목, 본문)를 입력해주세요."));
		}
		// recruitStartAt/recruitEndAt은 LocalDateTimeScalar가 서울 시각으로 저장한 값이라, now도 서울 시각 기준으로 맞춤
		LocalDateTime now = LocalDateTime.now(LocalDateTimeScalar.ZONE);
		LocalDateTime recruitStartAt = posting.getRecruitStartAt();
		LocalDateTime recruitEndAt = posting.getRecruitEndAt();
		if (recruitStartAt == null || recruitEndAt == null) {
			fieldErrors.add(new FieldErrorResponse("basicStep", "모집 기간을 입력해주세요."));
		} else if (!recruitEndAt.isAfter(recruitStartAt)) {
			fieldErrors.add(new FieldErrorResponse("basicStep", "모집 종료는 시작보다 뒤여야 해요."));
		} else if (!recruitEndAt.isAfter(now)) {
			fieldErrors.add(new FieldErrorResponse("basicStep", "모집 종료가 이미 지났어요."));
		}
		LocalDate activityStartedOn = posting.getActivityStartedOn();
		LocalDate activityEndedOn = posting.getActivityEndedOn();
		if (activityStartedOn != null && activityEndedOn != null && !activityEndedOn.isAfter(activityStartedOn)) {
			fieldErrors.add(new FieldErrorResponse("basicStep", "활동 종료는 시작보다 뒤여야 해요."));
		}
		if (!posting.isNoPosition() && positionRepository.findAllByPostingOrderBySortOrder(posting).isEmpty()) {
			fieldErrors.add(new FieldErrorResponse("positionsStep", "모집 포지션을 1개 이상 추가해주세요."));
		}
		if (posting.isHasInterview() && posting.getInterviewMethod() == null) {
			fieldErrors.add(new FieldErrorResponse("interviewStep", "면접 방식을 선택해주세요."));
		}
		if (questionRepository.findAllByPostingOrderBySortOrder(posting).isEmpty()) {
			fieldErrors.add(new FieldErrorResponse("questionsStep", "지원서 문항을 1개 이상 추가해주세요."));
		}
		if (!fieldErrors.isEmpty()) {
			throw new BusinessException(PostingErrorCode.INVALID_INPUT).withFieldErrors(fieldErrors);
		}
	}

	private void requireOperator(Long userId, Long organizationId) {
		if (!organizationAccessChecker.isOperatorOf(userId, organizationId)) {
			throw new BusinessException(CommonErrorCode.FORBIDDEN)
				.addContext("userId", userId)
				.addContext("organizationId", organizationId);
		}
	}

	private Posting findById(Long postingId) {
		return postingRepository.findById(postingId)
			.orElseThrow(() -> new BusinessException(PostingErrorCode.NOT_FOUND).addContext("postingId", postingId));
	}

	private static boolean touchesBasicStep(PostingDraftInput input) {
		return input.title() != null || input.content() != null || !input.semester().isOmitted()
			|| !input.region().isOmitted() || !input.fee().isOmitted() || input.allowEnrolled() != null
			|| input.allowOnLeave() != null || !input.eligibilityNote().isOmitted()
			|| !input.activityStartedOn().isOmitted() || !input.activityEndedOn().isOmitted()
			|| input.recruitStartAt() != null || input.recruitEndAt() != null || input.images() != null;
	}

	private static boolean touchesPositionsStep(PostingDraftInput input) {
		return input.noPosition() != null || input.positions() != null;
	}

	private static boolean touchesInterviewStep(PostingDraftInput input) {
		return input.hasInterview() != null || input.interviewMethod() != null
			|| input.interviewMinutes() != null || input.breakMinutes() != null
			|| input.assignRule() != null || input.maxApplicantsPerInterview() != null
			|| input.maxParallelInterviews() != null || input.interviewTimeRanges() != null;
	}

	private static boolean touchesQuestionsStep(PostingDraftInput input) {
		return input.docScorePerQuestion() != null || input.questions() != null;
	}

	private static Specification<Posting> addIfPresent(Specification<Posting> spec, Specification<Posting> toAdd) {
		return toAdd == null ? spec : spec.and(toAdd);
	}

	private static <T> T orElse(T value, T fallback) {
		return value != null ? value : fallback;
	}

	// 필드를 안 보냄(omitted) = 기존 값 유지, null을 보냄 = 지움
	private static <T> T resolve(ArgumentValue<T> value, T fallback) {
		return value.isOmitted() ? fallback : value.value();
	}

}
