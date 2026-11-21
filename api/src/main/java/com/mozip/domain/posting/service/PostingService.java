package com.mozip.domain.posting.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.domain.organization.entity.Organization;
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

	@Transactional
	public Posting saveDraft(Long userId, Long organizationId, Long postingId, PostingDraftInput input) {
		requireOperator(userId, organizationId);

		Posting posting;
		if (postingId == null) {
			posting = createDraft(organizationId, input);
		} else {
			posting = findById(postingId);
			if (!posting.isDraft()) {
				throw new BusinessException(PostingErrorCode.CONFLICT, PostingConflictReason.NOT_DRAFT)
					.addContext("postingId", postingId);
			}
		}

		applyBasicStep(posting, input);
		applyPositionsStep(posting, input);
		applyInterviewStep(posting, input);
		applyQuestionsStep(posting, input);
		applyExposureStep(posting, input);

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

		if (posting.getPublishAt() == null) {
			posting.publishNow();
		}
		// publishAt이 있으면 DRAFT로 두고 스케줄러(4단계 이슈)가 그 시각에 RECRUITING으로 전환함
		return posting;
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
			spec = addIfPresent(spec, PostingSpecifications.closingSoon(filter.closingSoon()));
		}

		PostingSort effectiveSort = sort != null ? sort : PostingSort.LATEST;
		Pageable pageable = PageRequest.of(page, size, effectiveSort.toJpaSort());

		long totalCount = postingRepository.count(spec);
		List<Posting> items = postingRepository.findAll(spec, pageable).getContent();
		boolean hasNext = (long)(page + 1) * size < totalCount;
		return new PostingPage(items, totalCount, hasNext);
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
		posting.updateBasicStep(
			orElse(input.title(), posting.getTitle()),
			orElse(input.content(), posting.getContent()),
			orElse(input.semester(), posting.getSemester()),
			orElse(input.region(), posting.getRegion()),
			orElse(input.fee(), posting.getFee()),
			orElse(input.allowEnrolled(), posting.isAllowEnrolled()),
			orElse(input.allowOnLeave(), posting.isAllowOnLeave()),
			orElse(input.eligibilityNote(), posting.getEligibilityNote()),
			orElse(input.activityStartedOn(), posting.getActivityStartedOn()),
			orElse(input.activityEndedOn(), posting.getActivityEndedOn()),
			orElse(input.recruitStartAt(), posting.getRecruitStartAt()),
			orElse(input.recruitEndAt(), posting.getRecruitEndAt()));
		if (input.images() != null) {
			replaceImages(posting, input.images());
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
		if (input.hasInterview() == null) {
			return;
		}
		posting.updateInterviewStep(
			input.hasInterview(),
			orElse(input.interviewMethod(), posting.getInterviewMethod()),
			orElse(input.interviewMinutes(), posting.getInterviewMinutes()),
			orElse(input.breakMinutes(), posting.getBreakMinutes()),
			orElse(input.assignRule(), posting.getAssignRule()),
			orElse(input.maxApplicantsPerInterview(), posting.getMaxApplicantsPerInterview()),
			orElse(input.maxParallelInterviews(), posting.getMaxParallelInterviews()));
		if (input.interviewTimeRanges() != null) {
			replaceInterviewTimeRanges(posting, input.interviewTimeRanges());
		} else if (!input.hasInterview()) {
			// 면접 없음으로 바꾸면서 interviewTimeRanges를 안 보내도 이전에 저장된 시간대는 지워야 함
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

	private void applyExposureStep(Posting posting, PostingDraftInput input) {
		if (!touchesExposureStep(input)) {
			return;
		}
		posting.updateExposureStep(orElse(input.publishAt(), posting.getPublishAt()));
		if (input.representativeImageId() != null) {
			markRepresentativeImage(posting, input.representativeImageId());
		}
	}

	private void replaceImages(Posting posting, List<PostingImageInput> imageInputs) {
		if (imageInputs.size() > MAX_IMAGES) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT)
				.addContext("imageCount", imageInputs.size());
		}
		// 이미지를 통째로 교체해도 대표 지정은 url로 맞춰서 유지함 (안 그러면 새 행은 전부 isRepresentative=false로 시작해 대표가 사라짐)
		String representativeUrl = postingImageRepository.findAllByPostingOrderBySortOrder(posting).stream()
			.filter(PostingImage::isRepresentative)
			.map(PostingImage::getUrl)
			.findFirst()
			.orElse(null);
		postingImageRepository.deleteAllByPosting(posting);
		List<PostingImage> images = imageInputs.stream()
			.map(i -> PostingImage.create(posting, i.url(), i.sortOrder()))
			.toList();
		postingImageRepository.saveAll(images);
		if (representativeUrl != null) {
			images.stream()
				.filter(image -> image.getUrl().equals(representativeUrl))
				.findFirst()
				.ifPresent(PostingImage::markRepresentative);
		}
	}

	private void replacePositions(Posting posting, List<PositionInput> positionInputs) {
		positionRepository.deleteAllByPosting(posting);
		List<Position> positions = positionInputs.stream()
			.map(i -> Position.create(posting, i.teamId() == null ? null : teamRepository.findById(i.teamId()).orElse(null),
				i.name(), i.headcount(), i.sortOrder()))
			.toList();
		positionRepository.saveAll(positions);
	}

	private void replaceQuestions(Posting posting, List<QuestionInput> questionInputs) {
		questionRepository.deleteAllByPosting(posting);
		List<Question> questions = new ArrayList<>();
		for (QuestionInput i : questionInputs) {
			Position position = i.positionId() == null ? null : positionRepository.findById(i.positionId()).orElse(null);
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
		List<InterviewTimeRange> ranges = new ArrayList<>();
		for (InterviewTimeRangeInput i : rangeInputs) {
			Position position = i.positionId() == null ? null : positionRepository.findById(i.positionId()).orElse(null);
			ranges.add(InterviewTimeRange.create(posting, position, i.date(), i.startTime(), i.endTime(),
				i.closedSlots()));
		}
		interviewTimeRangeRepository.saveAll(ranges);
	}

	// 대표는 한 번에 하나만 있어야 하므로 고른 것 외엔 전부 내림
	private void markRepresentativeImage(Posting posting, Long imageId) {
		List<PostingImage> images = postingImageRepository.findAllByPostingOrderBySortOrder(posting);
		for (PostingImage image : images) {
			if (image.getId().equals(imageId)) {
				image.markRepresentative();
			} else {
				image.unmarkRepresentative();
			}
		}
	}

	// 필수 단계 누락 검사. 문제 있는 단계를 fieldErrors에 전부 모아서 한 번에 알림
	private void validateRequiredSteps(Posting posting) {
		List<FieldErrorResponse> fieldErrors = new ArrayList<>();
		if (posting.getTitle() == null || posting.getTitle().isBlank() || posting.getContent() == null) {
			fieldErrors.add(new FieldErrorResponse("basicStep", "기본 정보(제목, 본문)를 입력해주세요."));
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
		return input.title() != null || input.content() != null || input.semester() != null
			|| input.region() != null || input.fee() != null || input.allowEnrolled() != null
			|| input.allowOnLeave() != null || input.eligibilityNote() != null || input.activityStartedOn() != null
			|| input.activityEndedOn() != null || input.recruitStartAt() != null || input.recruitEndAt() != null
			|| input.images() != null;
	}

	private static boolean touchesPositionsStep(PostingDraftInput input) {
		return input.noPosition() != null || input.positions() != null;
	}

	private static boolean touchesQuestionsStep(PostingDraftInput input) {
		return input.docScorePerQuestion() != null || input.questions() != null;
	}

	private static boolean touchesExposureStep(PostingDraftInput input) {
		return input.publishAt() != null || input.representativeImageId() != null;
	}

	private static Specification<Posting> addIfPresent(Specification<Posting> spec, Specification<Posting> toAdd) {
		return toAdd == null ? spec : spec.and(toAdd);
	}

	private static <T> T orElse(T value, T fallback) {
		return value != null ? value : fallback;
	}

}
