package com.mozip.domain.application.service;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.domain.application.dto.AnswerInput;
import com.mozip.domain.application.dto.ApplicationInput;
import com.mozip.domain.application.dto.AttachmentInput;
import com.mozip.domain.application.entity.Answer;
import com.mozip.domain.application.entity.Application;
import com.mozip.domain.application.entity.ApplicationStatus;
import com.mozip.domain.application.entity.Attachment;
import com.mozip.domain.application.entity.AttachmentType;
import com.mozip.domain.application.entity.DocResult;
import com.mozip.domain.application.entity.FinalResult;
import com.mozip.domain.application.exception.ApplicationConflictReason;
import com.mozip.domain.application.exception.ApplicationErrorCode;
import com.mozip.domain.application.exception.ApplicationForbiddenReason;
import com.mozip.domain.application.exception.ApplicationInputReason;
import com.mozip.domain.application.repository.AnswerRepository;
import com.mozip.domain.application.repository.ApplicationRepository;
import com.mozip.domain.application.repository.AttachmentRepository;
import com.mozip.domain.interview.service.InterviewAvailabilityService;
import com.mozip.domain.interview.timetable.InterviewSetting;
import com.mozip.domain.interview.timetable.InterviewSlotCalculator;
import com.mozip.domain.interview.timetable.Slot;
import com.mozip.domain.interview.timetable.TimeRange;
import com.mozip.domain.organization.service.OrganizationAccessChecker;
import com.mozip.domain.posting.entity.InterviewTimeRange;
import com.mozip.domain.posting.entity.Position;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.entity.PostingStatus;
import com.mozip.domain.posting.entity.Question;
import com.mozip.domain.posting.entity.QuestionType;
import com.mozip.domain.posting.exception.PostingConflictReason;
import com.mozip.domain.posting.exception.PostingErrorCode;
import com.mozip.domain.posting.repository.InterviewTimeRangeRepository;
import com.mozip.domain.posting.repository.PositionRepository;
import com.mozip.domain.posting.repository.PostingRepository;
import com.mozip.domain.posting.repository.QuestionRepository;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.common.response.FieldErrorResponse;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationService {

	private final ApplicationRepository applicationRepository;
	private final AnswerRepository answerRepository;
	private final AttachmentRepository attachmentRepository;
	private final PostingRepository postingRepository;
	private final PositionRepository positionRepository;
	private final InterviewTimeRangeRepository interviewTimeRangeRepository;
	private final QuestionRepository questionRepository;
	private final UserRepository userRepository;
	private final InterviewAvailabilityService interviewAvailabilityService;
	private final OrganizationAccessChecker organizationAccessChecker;
	private final ObjectMapper objectMapper;
	private final Clock clock;

	@Transactional
	public Application saveDraft(Long userId, Long postingId, ApplicationInput input) {
		Posting posting = findPosting(postingId);
		requireRecruiting(posting);
		User user = findUser(userId);

		Application application = findOrCreateDraft(posting, user);
		if (!application.isDraft()) {
			throw new BusinessException(ApplicationErrorCode.CONFLICT, ApplicationConflictReason.ALREADY_SUBMITTED)
				.addContext("applicationId", application.getId());
		}

		Position position = resolvePosition(posting, input.positionId());
		// 면접 없는 공고는 availableTimes를 받아도 저장하지 않음
		List<LocalDateTime> availableTimes = posting.isHasInterview() && input.availableTimes() != null
			? input.availableTimes() : List.of();
		validateAvailableTimes(posting, position, availableTimes);
		application.updateDraft(position, availableTimes);
		replaceAnswers(application, posting, position, input.answers());

		if (posting.isHasInterview()) {
			interviewAvailabilityService.save(application.getId(), availableTimes);
		}
		return application;
	}

	// 동시에 같은 (posting, user)로 saveApplicationDraft가 들어오면 uk_applications_posting_id_user_id를 어겨
	// DataIntegrityViolationException이 날 수 있음. 500 대신 재요청하면 되는 409로 바꿔서 던짐 (test.md)
	private Application findOrCreateDraft(Posting posting, User user) {
		return applicationRepository.findByPostingAndUser(posting, user)
			.orElseGet(() -> {
				try {
					return applicationRepository.save(Application.create(posting, null, user));
				} catch (DataIntegrityViolationException e) {
					throw new BusinessException(ApplicationErrorCode.CONFLICT, ApplicationConflictReason.CONCURRENT_SAVE)
						.addContext("postingId", posting.getId())
						.addContext("userId", user.getId());
				}
			});
	}

	@Transactional
	public Application submit(Long userId, Long applicationId) {
		Application application = findById(applicationId);
		if (!application.isOwnedBy(userId)) {
			throw new BusinessException(CommonErrorCode.FORBIDDEN)
				.addContext("userId", userId)
				.addContext("applicationId", applicationId);
		}
		if (!application.isDraft()) {
			throw new BusinessException(ApplicationErrorCode.CONFLICT, ApplicationConflictReason.ALREADY_SUBMITTED)
				.addContext("applicationId", applicationId);
		}
		Posting posting = application.getPosting();
		requireRecruiting(posting);

		User user = application.getUser();
		if (!user.isSchoolVerified()) {
			throw new BusinessException(ApplicationErrorCode.FORBIDDEN, ApplicationForbiddenReason.SCHOOL_NOT_VERIFIED)
				.addContext("userId", userId);
		}

		validateAnswers(application, posting);

		application.submit(buildProfileSnapshot(user), LocalDateTime.now(clock));
		return application;
	}

	// 운영진은 제출 전(DRAFT) 지원서는 못 봄. 지원자가 제출 버튼을 누르기 전 내용까지 보이면 안 됨
	public Application findDetail(Long applicationId, Long requestUserId) {
		Application application = findById(applicationId);
		boolean isOwner = application.isOwnedBy(requestUserId);
		boolean isOperatorViewable = !application.isDraft() && isOperatorOfPosting(application, requestUserId);
		if (!isOwner && !isOperatorViewable) {
			throw new BusinessException(CommonErrorCode.FORBIDDEN)
				.addContext("userId", requestUserId)
				.addContext("applicationId", applicationId);
		}
		return application;
	}

	// posting을 join fetch로 다시 조회해서, 지연 로딩된 application.getPosting()의 organization에 안전하게 접근함
	private boolean isOperatorOfPosting(Application application, Long userId) {
		Posting posting = postingRepository.findByIdWithOrganization(application.getPosting().getId())
			.orElseThrow(() -> new BusinessException(PostingErrorCode.NOT_FOUND)
				.addContext("postingId", application.getPosting().getId()));
		return organizationAccessChecker.isOperatorOf(userId, posting.getOrganization().getId());
	}

	public List<Application> findMyApplications(Long userId, ApplicationStatus status) {
		User user = findUser(userId);
		return status == null
			? applicationRepository.findAllByUserOrderByCreatedAtDesc(user)
			: applicationRepository.findAllByUserAndStatusOrderByCreatedAtDesc(user, status);
	}

	// B의 서류 평가 대상 조회용 연결 함수 (A -> B, ab-boundary.md)
	public List<Application> getSubmittedApplications(Long postingId) {
		Posting posting = findPosting(postingId);
		return applicationRepository.findAllByPostingAndStatus(posting, ApplicationStatus.SUBMITTED);
	}

	// 공고가 결과 발표 단계까지 가기 전에는 지원자에게 PENDING으로 가림
	public DocResult resolveDocResult(Application application, PostingStatus postingStatus) {
		return postingStatus != null && postingStatus.isDocResultVisible()
			? application.getDocResult()
			: DocResult.PENDING;
	}

	public FinalResult resolveFinalResult(Application application, PostingStatus postingStatus) {
		return postingStatus != null && postingStatus.isFinalResultVisible()
			? application.getFinalResult()
			: FinalResult.PENDING;
	}

	private void replaceAnswers(Application application, Posting posting, Position position,
		List<AnswerInput> answerInputs) {
		answerRepository.deleteAllByApplication(application);
		if (answerInputs == null || answerInputs.isEmpty()) {
			return;
		}
		// 답변마다 따로 조회하면 N+1이라, 문항을 한 번에 묶어서 가져옴
		List<Long> questionIds = answerInputs.stream().map(AnswerInput::questionId).distinct().toList();
		Map<Long, Question> questionById = questionRepository.findAllById(questionIds).stream()
			.collect(Collectors.toMap(Question::getId, Function.identity()));

		for (AnswerInput i : answerInputs) {
			Question question = questionById.get(i.questionId());
			if (question == null) {
				throw new BusinessException(CommonErrorCode.INVALID_INPUT)
					.addContext("questionId", i.questionId());
			}
			if (!question.getPosting().getId().equals(posting.getId())) {
				throw new BusinessException(CommonErrorCode.INVALID_INPUT, ApplicationInputReason.QUESTION_FROM_ANOTHER_POSTING)
					.addContext("questionId", i.questionId());
			}
			if (question.getPosition() != null
				&& (position == null || !question.getPosition().getId().equals(position.getId()))) {
				throw new BusinessException(CommonErrorCode.INVALID_INPUT, ApplicationInputReason.QUESTION_NOT_FOR_POSITION)
					.addContext("questionId", i.questionId());
			}
			validateAttachments(question, i.attachments());
			Answer answer = Answer.create(application, question, i.content());
			answerRepository.save(answer);
			if (i.attachments() != null) {
				for (AttachmentInput a : i.attachments()) {
					attachmentRepository.save(Attachment.create(answer, a.type(), a.url(), a.fileName()));
				}
			}
		}
	}

	// S3 presigned URL(#34)이 발급하는 키 접두어. FilePurpose.APPLICATION_ATTACHMENT.keyPrefix()와 같은 값이라
	// #34가 머지되면 그쪽 enum을 직접 참조하도록 바꿔야 함
	private static final String ATTACHMENT_FILE_PATH_PREFIX = "/application-attachment/";

	// 어노테이션으로 표현 못 하는, 타입에 따라 달라지는 검사. FILE_LINK가 아닌 문항에 첨부가 오면 막고,
	// LINK는 http(s) 주소만, FILE은 APPLICATION_ATTACHMENT 용도로 올린 경로만 허용
	private void validateAttachments(Question question, List<AttachmentInput> attachments) {
		if (attachments == null || attachments.isEmpty()) {
			return;
		}
		if (question.getType() != QuestionType.FILE_LINK) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT,
				ApplicationInputReason.ATTACHMENT_NOT_ALLOWED_FOR_QUESTION_TYPE)
				.addContext("questionId", question.getId());
		}
		for (AttachmentInput a : attachments) {
			boolean valid = a.type() == AttachmentType.LINK ? isHttpUrl(a.url()) : isAttachmentFileUrl(a.url());
			if (!valid) {
				throw new BusinessException(CommonErrorCode.INVALID_INPUT, ApplicationInputReason.INVALID_ATTACHMENT_URL)
					.addContext("questionId", question.getId())
					.addContext("url", a.url());
			}
		}
	}

	private boolean isHttpUrl(String url) {
		try {
			String scheme = URI.create(url).getScheme();
			return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	private boolean isAttachmentFileUrl(String url) {
		try {
			String path = URI.create(url).getPath();
			return path != null && path.startsWith(ATTACHMENT_FILE_PATH_PREFIX);
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	private void validateAnswers(Application application, Posting posting) {
		List<Question> questions = questionRepository.findAllByPostingOrderBySortOrder(posting);
		List<Answer> answers = answerRepository.findAllByApplicationOrderById(application);
		Map<Long, Answer> answerByQuestionId = new LinkedHashMap<>();
		for (Answer answer : answers) {
			answerByQuestionId.put(answer.getQuestion().getId(), answer);
		}
		// 문항마다 따로 조회하면 N+1이라, 답변들의 첨부를 한 번에 묶어서 가져와 답변 id별 개수로 센다
		Map<Long, Integer> attachmentCountByAnswerId = new LinkedHashMap<>();
		if (!answers.isEmpty()) {
			for (Attachment attachment : attachmentRepository.findAllByAnswerInOrderById(answers)) {
				attachmentCountByAnswerId.merge(attachment.getAnswer().getId(), 1, Integer::sum);
			}
		}

		Position position = application.getPosition();
		List<FieldErrorResponse> fieldErrors = new ArrayList<>();
		if (!posting.isNoPosition() && position == null) {
			fieldErrors.add(new FieldErrorResponse("position", "지원 포지션을 선택해주세요."));
		}
		for (Question question : questions) {
			if (question.getPosition() != null
				&& (position == null || !question.getPosition().getId().equals(position.getId()))) {
				continue;
			}
			Answer answer = answerByQuestionId.get(question.getId());
			int attachmentCount = answer == null ? 0 : attachmentCountByAnswerId.getOrDefault(answer.getId(), 0);
			validateAnswer(question, answer, attachmentCount, fieldErrors);
		}
		// 면접 공고인데 saveApplicationDraft에서 availableTimes를 한 번도 안 골랐으면 제출 시점에 막음
		if (posting.isHasInterview() && application.getAvailableTimes().isEmpty()) {
			fieldErrors.add(new FieldErrorResponse("availableTimes", "면접 가능 시간을 선택해주세요."));
		}
		if (!fieldErrors.isEmpty()) {
			throw new BusinessException(ApplicationErrorCode.INVALID_INPUT).withFieldErrors(fieldErrors);
		}
	}

	private void validateAnswer(Question question, Answer answer, int attachmentCount,
		List<FieldErrorResponse> fieldErrors) {
		String field = "question:" + question.getId();
		boolean blank = answer == null || answer.getContent() == null || answer.getContent().isBlank();

		if (question.isRequired() && question.getType() != QuestionType.FILE_LINK && blank) {
			fieldErrors.add(new FieldErrorResponse(field, "필수 문항입니다."));
			return;
		}
		if (question.isRequired() && question.getType() == QuestionType.FILE_LINK && attachmentCount == 0) {
			fieldErrors.add(new FieldErrorResponse(field, "필수 첨부입니다."));
			return;
		}
		if (answer == null) {
			return;
		}
		if (question.getType() == QuestionType.TEXT && question.getMaxLength() != null
			&& answer.getContent() != null && answer.getContent().length() > question.getMaxLength()) {
			fieldErrors.add(new FieldErrorResponse(field, "글자 수 제한을 넘었습니다."));
		}
		if (question.getType() == QuestionType.FILE_LINK && question.getMaxFiles() != null
			&& attachmentCount > question.getMaxFiles()) {
			fieldErrors.add(new FieldErrorResponse(field, "첨부 개수 제한을 넘었습니다."));
		}
		if (question.getType() == QuestionType.SINGLE_CHOICE && !blank
			&& !isValidChoice(question, answer.getContent())) {
			fieldErrors.add(new FieldErrorResponse(field, "선택지에 없는 값입니다."));
		}
		if (question.getType() == QuestionType.MULTI_CHOICE && !blank) {
			List<String> selected = readStringList(answer.getContent());
			if (selected == null || !selected.stream().allMatch(c -> isValidChoice(question, c))) {
				fieldErrors.add(new FieldErrorResponse(field, "선택지에 없는 값입니다."));
			} else if (selected.isEmpty() && question.isRequired()) {
				fieldErrors.add(new FieldErrorResponse(field, "필수 문항입니다."));
			}
		}
		if (question.getType() == QuestionType.TABLE && !blank) {
			List<List<String>> rows = readTable(answer.getContent());
			if (rows == null) {
				fieldErrors.add(new FieldErrorResponse(field, "표 형식이 올바르지 않습니다."));
			} else if (rows.isEmpty() && question.isRequired()) {
				fieldErrors.add(new FieldErrorResponse(field, "필수 문항입니다."));
			} else if (question.getMaxRows() != null && rows.size() > question.getMaxRows()) {
				fieldErrors.add(new FieldErrorResponse(field, "행 수 제한을 넘었습니다."));
			} else if (question.getColumns() != null
				&& rows.stream().anyMatch(row -> row.size() != question.getColumns().size())) {
				fieldErrors.add(new FieldErrorResponse(field, "열 구성이 문항과 다릅니다."));
			}
		}
	}

	private boolean isValidChoice(Question question, String choice) {
		return question.getChoices() != null && question.getChoices().contains(choice);
	}

	// 운영진이 연 시간대(InterviewTimeRange)를 블록으로 잘라서, 고른 시간이 실제로 열려 있는 블록의 시작 시각인지 확인
	private void validateAvailableTimes(Posting posting, Position position, List<LocalDateTime> availableTimes) {
		if (availableTimes.isEmpty()) {
			return;
		}
		Set<LocalDateTime> openStartTimes = openSlotStartTimes(posting, position);
		for (LocalDateTime time : availableTimes) {
			if (!openStartTimes.contains(time)) {
				throw new BusinessException(CommonErrorCode.INVALID_INPUT, ApplicationInputReason.AVAILABLE_TIME_NOT_OPEN)
					.addContext("postingId", posting.getId())
					.addContext("availableTime", time);
			}
		}
	}

	private Set<LocalDateTime> openSlotStartTimes(Posting posting, Position position) {
		if (posting.getInterviewMinutes() == null || posting.getBreakMinutes() == null
			|| posting.getMaxApplicantsPerInterview() == null || posting.getMaxParallelInterviews() == null
			|| posting.getAssignRule() == null) {
			return Set.of();
		}
		List<TimeRange> ranges = interviewTimeRangeRepository.findAllByPostingOrderByDateAscStartTimeAsc(posting)
			.stream()
			.filter(range -> range.getPosition() == null
				|| (position != null && range.getPosition().getId().equals(position.getId())))
			.map(this::toTimeRange)
			.toList();
		if (ranges.isEmpty()) {
			return Set.of();
		}
		InterviewSetting setting = new InterviewSetting(posting.getInterviewMinutes(), posting.getBreakMinutes(),
			posting.getMaxApplicantsPerInterview(), posting.getMaxParallelInterviews(), posting.getAssignRule());
		return InterviewSlotCalculator.calculate(ranges, setting).stream()
			.filter(slot -> !slot.closed())
			.map(Slot::startAt)
			.collect(Collectors.toSet());
	}

	private TimeRange toTimeRange(InterviewTimeRange range) {
		List<LocalTime> closedSlots = range.getClosedSlots();
		return new TimeRange(range.getDate(), range.getStartTime(), range.getEndTime(),
			closedSlots == null ? Set.of() : Set.copyOf(closedSlots));
	}

	// 선택지는 문자열이어야 함. [1]처럼 다른 타입이 섞여 있으면 선택지 불일치로 처리 (원소를 String으로 바로 캐스팅하면 ClassCastException)
	private List<String> readStringList(String json) {
		List<?> raw;
		try {
			raw = objectMapper.readValue(json, List.class);
		} catch (RuntimeException e) {
			return null;
		}
		if (raw == null) {
			return null;
		}
		for (Object element : raw) {
			if (!(element instanceof String)) {
				return null;
			}
		}
		return (List<String>)raw;
	}

	private List<List<String>> readTable(String json) {
		try {
			return objectMapper.readValue(json, new TypeReference<List<List<String>>>() {
			});
		} catch (RuntimeException e) {
			return null;
		}
	}

	private Position resolvePosition(Posting posting, Long positionId) {
		if (positionId == null) {
			return null;
		}
		Position position = positionRepository.findById(positionId)
			.orElseThrow(() -> new BusinessException(CommonErrorCode.INVALID_INPUT)
				.addContext("positionId", positionId));
		if (!position.getPosting().getId().equals(posting.getId())) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT, ApplicationInputReason.POSITION_FROM_ANOTHER_POSTING)
				.addContext("positionId", positionId);
		}
		return position;
	}

	private void requireRecruiting(Posting posting) {
		// recruitStartAt/recruitEndAt은 LocalDateTimeScalar가 서울 시각으로 저장한 값이라, now도 서울 시각 기준으로 맞춤
		LocalDateTime now = LocalDateTime.now(clock);
		boolean withinWindow = (posting.getRecruitStartAt() == null || !now.isBefore(posting.getRecruitStartAt()))
			&& (posting.getRecruitEndAt() == null || !now.isAfter(posting.getRecruitEndAt()));
		if (posting.getStatus() != PostingStatus.RECRUITING || !withinWindow) {
			throw new BusinessException(PostingErrorCode.CONFLICT, PostingConflictReason.NOT_RECRUITING)
				.addContext("postingId", posting.getId());
		}
	}

	private String buildProfileSnapshot(User user) {
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("name", user.getName());
		snapshot.put("phone", user.getPhone());
		// TODO(A 회원 프로필): 학과·학년·활동 경험은 User에 아직 필드가 없어서 뺌. 프로필 기능 추가되면 채움
		snapshot.put("school", user.getSchool() == null ? null : user.getSchool().getName());
		return objectMapper.writeValueAsString(snapshot);
	}

	private Posting findPosting(Long postingId) {
		return postingRepository.findById(postingId)
			.orElseThrow(() -> new BusinessException(PostingErrorCode.NOT_FOUND).addContext("postingId", postingId));
	}

	private User findUser(Long userId) {
		return userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(CommonErrorCode.INVALID_INPUT).addContext("userId", userId));
	}

	private Application findById(Long applicationId) {
		return applicationRepository.findById(applicationId)
			.orElseThrow(() -> new BusinessException(ApplicationErrorCode.NOT_FOUND)
				.addContext("applicationId", applicationId));
	}

}
