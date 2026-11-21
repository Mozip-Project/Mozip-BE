package com.mozip.domain.application.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.domain.application.dto.AnswerInput;
import com.mozip.domain.application.dto.ApplicationInput;
import com.mozip.domain.application.dto.AttachmentInput;
import com.mozip.domain.application.entity.Answer;
import com.mozip.domain.application.entity.Application;
import com.mozip.domain.application.entity.ApplicationStatus;
import com.mozip.domain.application.entity.Attachment;
import com.mozip.domain.application.exception.ApplicationConflictReason;
import com.mozip.domain.application.exception.ApplicationErrorCode;
import com.mozip.domain.application.repository.AnswerRepository;
import com.mozip.domain.application.repository.ApplicationRepository;
import com.mozip.domain.application.repository.AttachmentRepository;
import com.mozip.domain.interview.service.InterviewAvailabilityService;
import com.mozip.domain.organization.service.OrganizationAccessChecker;
import com.mozip.domain.posting.entity.Position;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.entity.PostingStatus;
import com.mozip.domain.posting.entity.Question;
import com.mozip.domain.posting.entity.QuestionType;
import com.mozip.domain.posting.exception.PostingConflictReason;
import com.mozip.domain.posting.exception.PostingErrorCode;
import com.mozip.domain.posting.repository.PositionRepository;
import com.mozip.domain.posting.repository.PostingRepository;
import com.mozip.domain.posting.repository.QuestionRepository;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.exception.UserErrorCode;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.common.response.FieldErrorResponse;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;

import lombok.RequiredArgsConstructor;
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
	private final QuestionRepository questionRepository;
	private final UserRepository userRepository;
	private final InterviewAvailabilityService interviewAvailabilityService;
	private final OrganizationAccessChecker organizationAccessChecker;
	private final ObjectMapper objectMapper;

	@Transactional
	public Application saveDraft(Long userId, Long postingId, ApplicationInput input) {
		Posting posting = findPosting(postingId);
		requireRecruiting(posting);
		User user = findUser(userId);

		Application application = applicationRepository.findByPostingAndUser(posting, user)
			.orElseGet(() -> applicationRepository.save(Application.create(posting, null, user)));
		if (!application.isDraft()) {
			throw new BusinessException(ApplicationErrorCode.CONFLICT, ApplicationConflictReason.ALREADY_SUBMITTED)
				.addContext("applicationId", application.getId());
		}

		Position position = resolvePosition(posting, input.positionId());
		application.updateDraft(position, input.availableTimes());
		replaceAnswers(application, posting, position, input.answers());

		if (posting.isHasInterview()) {
			interviewAvailabilityService.save(application.getId(), input.availableTimes());
		}
		return application;
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
		if (user.getSchool() == null) {
			throw new BusinessException(UserErrorCode.INELIGIBLE)
				.addContext("userId", userId)
				.addContext("reason", "학교 인증 안 됨");
		}

		validateAnswers(application, posting);

		application.submit(buildProfileSnapshot(user));
		return application;
	}

	public Application findDetail(Long applicationId, Long requestUserId) {
		Application application = findById(applicationId);
		if (!application.isOwnedBy(requestUserId) && !isOperatorOfPosting(application, requestUserId)) {
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

	private void replaceAnswers(Application application, Posting posting, Position position,
		List<AnswerInput> answerInputs) {
		answerRepository.deleteAllByApplication(application);
		if (answerInputs == null) {
			return;
		}
		for (AnswerInput i : answerInputs) {
			Question question = questionRepository.findById(i.questionId())
				.orElseThrow(() -> new BusinessException(CommonErrorCode.INVALID_INPUT)
					.addContext("questionId", i.questionId()));
			if (!question.getPosting().getId().equals(posting.getId())) {
				throw new BusinessException(CommonErrorCode.INVALID_INPUT)
					.addContext("reason", "다른 공고의 문항입니다")
					.addContext("questionId", i.questionId());
			}
			if (question.getPosition() != null
				&& (position == null || !question.getPosition().getId().equals(position.getId()))) {
				throw new BusinessException(CommonErrorCode.INVALID_INPUT)
					.addContext("reason", "선택한 포지션의 문항이 아닙니다")
					.addContext("questionId", i.questionId());
			}
			Answer answer = Answer.create(application, question, i.content());
			answerRepository.save(answer);
			if (i.attachments() != null) {
				for (AttachmentInput a : i.attachments()) {
					attachmentRepository.save(Attachment.create(answer, a.type(), a.url(), a.fileName()));
				}
			}
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
		for (Question question : questions) {
			if (question.getPosition() != null
				&& (position == null || !question.getPosition().getId().equals(position.getId()))) {
				continue;
			}
			Answer answer = answerByQuestionId.get(question.getId());
			int attachmentCount = answer == null ? 0 : attachmentCountByAnswerId.getOrDefault(answer.getId(), 0);
			validateAnswer(question, answer, attachmentCount, fieldErrors);
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
			if (selected == null || selected.isEmpty() || !selected.stream().allMatch(c -> isValidChoice(question, c))) {
				fieldErrors.add(new FieldErrorResponse(field, "선택지에 없는 값입니다."));
			}
		}
	}

	private boolean isValidChoice(Question question, String choice) {
		return question.getChoices() != null && question.getChoices().contains(choice);
	}

	private List<String> readStringList(String json) {
		try {
			return objectMapper.readValue(json, List.class);
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
			throw new BusinessException(CommonErrorCode.INVALID_INPUT)
				.addContext("reason", "다른 공고의 포지션입니다")
				.addContext("positionId", positionId);
		}
		return position;
	}

	private void requireRecruiting(Posting posting) {
		LocalDateTime now = LocalDateTime.now();
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
