package com.mozip.domain.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.TimeZone;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.data.ArgumentValue;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.TestcontainersConfiguration;
import com.mozip.domain.application.dto.AnswerInput;
import com.mozip.domain.application.dto.ApplicationInput;
import com.mozip.domain.application.dto.AttachmentInput;
import com.mozip.domain.application.entity.Answer;
import com.mozip.domain.application.entity.Application;
import com.mozip.domain.application.entity.ApplicationStatus;
import com.mozip.domain.application.entity.AttachmentType;
import com.mozip.domain.application.exception.ApplicationConflictReason;
import com.mozip.domain.application.exception.ApplicationErrorCode;
import com.mozip.domain.application.exception.ApplicationForbiddenReason;
import com.mozip.domain.application.exception.ApplicationInputReason;
import com.mozip.domain.application.repository.AnswerRepository;
import com.mozip.domain.application.repository.ApplicationRepository;
import com.mozip.domain.application.repository.AttachmentRepository;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.OrganizationScope;
import com.mozip.domain.organization.entity.OrganizationType;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.repository.OrganizationRepository;
import com.mozip.domain.posting.dto.InterviewTimeRangeInput;
import com.mozip.domain.posting.dto.PositionInput;
import com.mozip.domain.posting.dto.PostingDraftInput;
import com.mozip.domain.posting.dto.QuestionInput;
import com.mozip.domain.posting.dto.QuestionOptionsInput;
import com.mozip.domain.posting.entity.AssignRule;
import com.mozip.domain.posting.entity.Position;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.entity.QuestionType;
import com.mozip.domain.posting.exception.PostingConflictReason;
import com.mozip.domain.posting.exception.PostingErrorCode;
import com.mozip.domain.posting.repository.PositionRepository;
import com.mozip.domain.posting.repository.QuestionRepository;
import com.mozip.domain.posting.service.PostingService;
import com.mozip.domain.school.entity.School;
import com.mozip.domain.school.repository.SchoolRepository;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.common.response.FieldErrorResponse;
import com.mozip.global.config.graphql.LocalDateTimeScalar;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class ApplicationServiceTest {

	@Autowired
	private ApplicationService applicationService;

	@Autowired
	private PostingService postingService;

	@Autowired
	private ApplicationRepository applicationRepository;

	@Autowired
	private AnswerRepository answerRepository;

	@Autowired
	private AttachmentRepository attachmentRepository;

	@Autowired
	private PositionRepository positionRepository;

	@Autowired
	private QuestionRepository questionRepository;

	@Autowired
	private OrganizationRepository organizationRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private SchoolRepository schoolRepository;

	private Organization organization;
	private User operator;
	private User applicant;
	private School school;

	@BeforeEach
	void setUp() {
		organization = organizationRepository.save(
			Organization.create("테스트 단체", OrganizationType.CLUB, null, OrganizationScope.CAMPUS, null, true, null,
				null));
		operator = userRepository.save(User.create("01044440001", "encoded", "운영진", false, LocalDateTime.now()));
		memberRepository.save(Member.create(organization, operator, null, MemberRole.REPRESENTATIVE));
		applicant = userRepository.save(User.create("01044440002", "encoded", "지원자", false, LocalDateTime.now()));
		school = schoolRepository.save(School.create("성균관대학교", "skku.edu"));
	}

	@Test
	@DisplayName("처음 저장하면 DRAFT 지원서가 생성되고, 다시 저장하면 같은 지원서가 덮어써진다")
	void createsDraftThenOverwritesOnResave() {
		Posting posting = recruitingPostingWithPosition();
		Position position = onlyPosition(posting);
		Long questionId = onlyQuestionId(posting);

		Application created = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(new AnswerInput(questionId, "첫 답변", null)), null));

		assertThat(created.getStatus()).isEqualTo(ApplicationStatus.DRAFT);
		assertThat(answerRepository.findAllByApplicationOrderById(created)).hasSize(1);

		Application resaved = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(new AnswerInput(questionId, "수정한 답변", null)), null));

		assertThat(resaved.getId()).isEqualTo(created.getId());
		List<Answer> answers = answerRepository.findAllByApplicationOrderById(resaved);
		assertThat(answers).hasSize(1);
		assertThat(answers.get(0).getContent()).isEqualTo("수정한 답변");
		assertThat(applicationRepository.findAll()).hasSize(1);
	}

	@Test
	@DisplayName("면접 없는 공고에 availableTimes를 보내도 저장하지 않는다")
	void ignoresAvailableTimesWhenPostingHasNoInterview() {
		Posting posting = recruitingPostingWithPosition();
		Position position = onlyPosition(posting);

		Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(), List.of(LocalDateTime.now().plusDays(1))));

		assertThat(application.getAvailableTimes()).isEmpty();
	}

	@Test
	@DisplayName("면접 공고에서 열려 있는 시간대의 블록 시작 시각이면 availableTimes로 저장된다")
	void acceptsAvailableTimesMatchingOpenInterviewSlot() {
		LocalDate date = LocalDate.now().plusDays(10);
		Posting posting = recruitingPostingWithInterview(date, LocalTime.of(10, 0), LocalTime.of(11, 0));
		Position position = onlyPosition(posting);
		LocalDateTime validSlot = date.atTime(10, 0);

		Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(), List.of(validSlot)));

		assertThat(application.getAvailableTimes()).containsExactly(validSlot);
	}

	@Test
	@DisplayName("면접 공고에서 열려 있는 블록의 시작 시각이 아니면 COMMON_400을 던진다")
	void rejectsAvailableTimesNotMatchingAnyOpenSlot() {
		LocalDate date = LocalDate.now().plusDays(10);
		Posting posting = recruitingPostingWithInterview(date, LocalTime.of(10, 0), LocalTime.of(11, 0));
		Position position = onlyPosition(posting);
		LocalDateTime invalidSlot = date.atTime(10, 15);

		assertThatThrownBy(() -> applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(), List.of(invalidSlot))))
			.isInstanceOf(BusinessException.class)
			.satisfies(e -> {
				assertThat(((BusinessException)e).getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
				assertThat(((BusinessException)e).getReason()).isEqualTo(ApplicationInputReason.AVAILABLE_TIME_NOT_OPEN.name());
			});
	}

	@Test
	@DisplayName("다른 공고의 문항으로 답변하면 COMMON_400을 던진다")
	void rejectsAnswerForQuestionFromAnotherPosting() {
		Posting posting = recruitingPostingWithPosition();
		Position position = onlyPosition(posting);
		Posting otherPosting = recruitingPostingWithPosition();
		Long otherQuestionId = onlyQuestionId(otherPosting);

		assertThatThrownBy(() -> applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(new AnswerInput(otherQuestionId, "답변", null)), null)))
			.isInstanceOf(BusinessException.class)
			.satisfies(e -> {
				assertThat(((BusinessException)e).getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
				assertThat(((BusinessException)e).getReason()).isEqualTo(ApplicationInputReason.QUESTION_FROM_ANOTHER_POSTING.name());
			});
	}

	@Test
	@DisplayName("다른 공고의 포지션을 고르면 COMMON_400을 던진다")
	void rejectsPositionFromAnotherPosting() {
		Posting posting = recruitingPostingWithPosition();
		Posting otherPosting = recruitingPostingWithPosition();
		Position otherPosition = onlyPosition(otherPosting);

		assertThatThrownBy(() -> applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(otherPosition.getId(), List.of(), null)))
			.isInstanceOf(BusinessException.class)
			.satisfies(e -> {
				assertThat(((BusinessException)e).getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
				assertThat(((BusinessException)e).getReason()).isEqualTo(ApplicationInputReason.POSITION_FROM_ANOTHER_POSTING.name());
			});
	}

	@Test
	@DisplayName("제출된 지원서를 다시 저장(수정)하려 하면 APPLICATION_409(ALREADY_SUBMITTED)를 던진다")
	void rejectsSavingAfterSubmit() {
		Posting posting = recruitingPostingWithPosition();
		Position position = onlyPosition(posting);
		Long questionId = onlyQuestionId(posting);
		applicant.verifySchool(school);
		userRepository.save(applicant);
		Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(new AnswerInput(questionId, "답변", null)), null));
		applicationService.submit(applicant.getId(), application.getId());

		assertThatThrownBy(() -> applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(new AnswerInput(questionId, "수정 시도", null)), null)))
			.isInstanceOf(BusinessException.class)
			.satisfies(e -> {
				assertThat(((BusinessException)e).getErrorCode()).isEqualTo(ApplicationErrorCode.CONFLICT);
				assertThat(((BusinessException)e).getReason()).isEqualTo(ApplicationConflictReason.ALREADY_SUBMITTED.name());
			});
	}

	@Test
	@DisplayName("모집 기간이 아니면(게시 전 DRAFT 공고) POSTING_409(NOT_RECRUITING)를 던진다")
	void rejectsSavingWhenNotRecruiting() {
		Posting draftPosting = postingService.saveDraft(operator.getId(), organization.getId(), null,
			basicInput("아직 작성 중"));

		assertThatThrownBy(() -> applicationService.saveDraft(applicant.getId(), draftPosting.getId(),
			new ApplicationInput(null, null, null)))
			.isInstanceOf(BusinessException.class)
			.satisfies(e -> {
				assertThat(((BusinessException)e).getErrorCode()).isEqualTo(PostingErrorCode.CONFLICT);
				assertThat(((BusinessException)e).getReason()).isEqualTo(PostingConflictReason.NOT_RECRUITING.name());
			});
	}

	@Test
	@DisplayName("RECRUITING이어도 recruitEndAt이 지났으면 POSTING_409(NOT_RECRUITING)를 던진다")
	void rejectsSavingWhenRecruitWindowExpired() {
		// 공고 작성 시점엔 마감일이 미래여야 게시 가능(postingService.publish의 모집 기간 검증). 게시 후 시간이 지난 상황은
		// 엔티티를 직접 건드려서 흉내냄(스케줄러 테스트의 PostingRepositoryTest와 같은 방식)
		// publish의 모집 기간 검증(PostingService)은 서울 시각 기준이라, 여기서도 서울 시각으로 맞춰야
		// JVM 기본 시간대가 서울이 아닌 CI에서도 publish가 거부되지 않음
		LocalDateTime seoulNow = LocalDateTime.now(LocalDateTimeScalar.ZONE);
		Posting posting = postingService.saveDraft(operator.getId(), organization.getId(), null,
			basicInput("마감 지난 공고 " + System.nanoTime(), seoulNow.plusHours(1)));
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withPositions(List.of(new PositionInput(null, "백엔드", 1, 0))));
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withQuestions(List.of(new QuestionInput(null, QuestionType.TEXT, "자기소개", null, true, 500, null, 0))));
		postingService.publish(operator.getId(), posting.getId());
		posting.updateBasicStep(posting.getTitle(), posting.getContent(), posting.getSemester(), posting.getRegion(),
			posting.getFee(), posting.isAllowEnrolled(), posting.isAllowOnLeave(), posting.getEligibilityNote(),
			posting.getActivityStartedOn(), posting.getActivityEndedOn(), posting.getRecruitStartAt(),
			seoulNow.minusDays(1));

		assertThatThrownBy(() -> applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(null, null, null)))
			.isInstanceOf(BusinessException.class)
			.satisfies(e -> {
				assertThat(((BusinessException)e).getErrorCode()).isEqualTo(PostingErrorCode.CONFLICT);
				assertThat(((BusinessException)e).getReason()).isEqualTo(PostingConflictReason.NOT_RECRUITING.name());
			});
	}

	@Test
	@DisplayName("JVM 기본 시간대가 서울이 아니어도 recruitStartAt/recruitEndAt은 서울 시각 기준으로 판단한다")
	void judgesRecruitingWindowInSeoulTimeRegardlessOfJvmDefaultZone() {
		TimeZone originalDefault = TimeZone.getDefault();
		try {
			TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
			LocalDateTime seoulNow = LocalDateTime.now(LocalDateTimeScalar.ZONE);
			Posting posting = postingService.saveDraft(operator.getId(), organization.getId(), null,
				basicInputWithWindow("시간대 테스트 공고 " + System.nanoTime(), seoulNow.minusHours(1), seoulNow.plusDays(1)));
			postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
				withPositions(List.of(new PositionInput(null, "백엔드", 1, 0))));
			postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
				withQuestions(List.of(new QuestionInput(null, QuestionType.TEXT, "자기소개", null, true, 500, null, 0))));
			postingService.publish(operator.getId(), posting.getId());

			Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
				new ApplicationInput(null, null, null));

			assertThat(application.getStatus()).isEqualTo(ApplicationStatus.DRAFT);
		} finally {
			TimeZone.setDefault(originalDefault);
		}
	}

	@Test
	@DisplayName("필수 문항에 답하지 않고 제출하면 APPLICATION_400을 던지고 fieldErrors에 questionId가 담긴다")
	void rejectsSubmitWithMissingRequiredAnswer() {
		Posting posting = recruitingPostingWithPosition();
		Position position = onlyPosition(posting);
		Long questionId = onlyQuestionId(posting);
		applicant.verifySchool(school);
		userRepository.save(applicant);

		Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(), null));

		assertThatThrownBy(() -> applicationService.submit(applicant.getId(), application.getId()))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(ApplicationErrorCode.INVALID_INPUT);
				assertThat(e.getFieldErrors())
					.extracting(FieldErrorResponse::field)
					.containsExactly("question:" + questionId);
			});
	}

	@Test
	@DisplayName("면접 공고인데 availableTimes 없이 제출하면 APPLICATION_400을 던지고 fieldErrors에 availableTimes가 담긴다")
	void rejectsSubmitWithoutAvailableTimesWhenPostingHasInterview() {
		LocalDate date = LocalDate.now().plusDays(10);
		Posting posting = recruitingPostingWithInterview(date, LocalTime.of(10, 0), LocalTime.of(11, 0));
		Position position = onlyPosition(posting);
		Long questionId = onlyQuestionId(posting);
		applicant.verifySchool(school);
		userRepository.save(applicant);
		Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(new AnswerInput(questionId, "답변", null)), null));

		assertThatThrownBy(() -> applicationService.submit(applicant.getId(), application.getId()))
			.isInstanceOf(BusinessException.class)
			.satisfies(e -> {
				BusinessException businessException = (BusinessException)e;
				assertThat(businessException.getErrorCode()).isEqualTo(ApplicationErrorCode.INVALID_INPUT);
				assertThat(businessException.getFieldErrors()).extracting(FieldErrorResponse::field)
					.contains("availableTimes");
			});
	}

	@Test
	@DisplayName("면접 공고에서 열려 있는 시간을 고르고 제출하면 성공한다")
	void submitsSuccessfullyWithAvailableTimesWhenPostingHasInterview() {
		LocalDate date = LocalDate.now().plusDays(10);
		Posting posting = recruitingPostingWithInterview(date, LocalTime.of(10, 0), LocalTime.of(11, 0));
		Position position = onlyPosition(posting);
		Long questionId = onlyQuestionId(posting);
		applicant.verifySchool(school);
		userRepository.save(applicant);
		LocalDateTime validSlot = date.atTime(10, 0);
		Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(new AnswerInput(questionId, "답변", null)), List.of(validSlot)));

		Application submitted = applicationService.submit(applicant.getId(), application.getId());

		assertThat(submitted.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
	}

	@Test
	@DisplayName("학교 인증이 안 된 회원이 제출하면 APPLICATION_403(SCHOOL_NOT_VERIFIED)을 던진다")
	void rejectsSubmitWithoutSchoolVerification() {
		Posting posting = recruitingPostingWithPosition();
		Position position = onlyPosition(posting);
		Long questionId = onlyQuestionId(posting);
		Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(new AnswerInput(questionId, "답변", null)), null));

		assertThatThrownBy(() -> applicationService.submit(applicant.getId(), application.getId()))
			.isInstanceOf(BusinessException.class)
			.satisfies(e -> {
				assertThat(((BusinessException)e).getErrorCode()).isEqualTo(ApplicationErrorCode.FORBIDDEN);
				assertThat(((BusinessException)e).getReason()).isEqualTo(ApplicationForbiddenReason.SCHOOL_NOT_VERIFIED.name());
			});
	}

	@Test
	@DisplayName("제출하면 SUBMITTED로 바뀌고 submittedAt과 profileSnapshot이 채워진다")
	void submitsSuccessfully() {
		Posting posting = recruitingPostingWithPosition();
		Position position = onlyPosition(posting);
		Long questionId = onlyQuestionId(posting);
		applicant.verifySchool(school);
		userRepository.save(applicant);
		Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(new AnswerInput(questionId, "답변", null)), null));

		Application submitted = applicationService.submit(applicant.getId(), application.getId());

		assertThat(submitted.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
		assertThat(submitted.getSubmittedAt()).isNotNull();
		assertThat(submitted.getProfileSnapshot()).contains("지원자").contains("성균관대학교");
	}

	@Test
	@DisplayName("이미 제출한 지원서를 다시 제출하면 APPLICATION_409(ALREADY_SUBMITTED)를 던진다")
	void rejectsResubmitting() {
		Posting posting = recruitingPostingWithPosition();
		Position position = onlyPosition(posting);
		Long questionId = onlyQuestionId(posting);
		applicant.verifySchool(school);
		userRepository.save(applicant);
		Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(new AnswerInput(questionId, "답변", null)), null));
		applicationService.submit(applicant.getId(), application.getId());

		assertThatThrownBy(() -> applicationService.submit(applicant.getId(), application.getId()))
			.isInstanceOf(BusinessException.class)
			.satisfies(e -> {
				assertThat(((BusinessException)e).getErrorCode()).isEqualTo(ApplicationErrorCode.CONFLICT);
				assertThat(((BusinessException)e).getReason()).isEqualTo(ApplicationConflictReason.ALREADY_SUBMITTED.name());
			});
	}

	@Test
	@DisplayName("SINGLE_CHOICE 문항에 보기에 없는 값을 답하고 제출하면 APPLICATION_400을 던진다")
	void rejectsSubmitWithInvalidChoice() {
		Posting posting = postingService.saveDraft(operator.getId(), organization.getId(), null,
			basicInput("선택형 공고 " + System.nanoTime()));
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withPositions(List.of(new PositionInput(null, "백엔드", 1, 0))));
		Position position = onlyPosition(posting);
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withQuestions(List.of(new QuestionInput(null, QuestionType.SINGLE_CHOICE, "지원 경로", null, true, null,
				new QuestionOptionsInput(List.of("학교 홈페이지", "SNS"), null, null, null), 0))));
		postingService.publish(operator.getId(), posting.getId());
		Long questionId = onlyQuestionId(posting);
		applicant.verifySchool(school);
		userRepository.save(applicant);
		Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(new AnswerInput(questionId, "보기에 없는 값", null)), null));

		assertThatThrownBy(() -> applicationService.submit(applicant.getId(), application.getId()))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(ApplicationErrorCode.INVALID_INPUT);
	}

	@Test
	@DisplayName("SINGLE_CHOICE 문항에 보기에 있는 값으로 제출하면 성공한다")
	void submitsSuccessfullyWithValidSingleChoice() {
		Posting posting = recruitingPostingWithQuestion(QuestionType.SINGLE_CHOICE, true, null,
			new QuestionOptionsInput(List.of("학교 홈페이지", "SNS"), null, null, null));
		Position position = onlyPosition(posting);
		Long questionId = onlyQuestionId(posting);
		applicant.verifySchool(school);
		userRepository.save(applicant);
		Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(new AnswerInput(questionId, "SNS", null)), null));

		Application submitted = applicationService.submit(applicant.getId(), application.getId());

		assertThat(submitted.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
	}

	@Test
	@DisplayName("MULTI_CHOICE 문항에 선택지에 있는 값들로 제출하면 성공한다")
	void submitsSuccessfullyWithValidMultiChoice() {
		Posting posting = recruitingPostingWithQuestion(QuestionType.MULTI_CHOICE, true, null,
			new QuestionOptionsInput(List.of("A", "B", "C"), null, null, null));
		Position position = onlyPosition(posting);
		Long questionId = onlyQuestionId(posting);
		applicant.verifySchool(school);
		userRepository.save(applicant);
		Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(new AnswerInput(questionId, "[\"A\",\"B\"]", null)), null));

		Application submitted = applicationService.submit(applicant.getId(), application.getId());

		assertThat(submitted.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
	}

	@Test
	@DisplayName("MULTI_CHOICE 문항에 선택지에 없는 값이 섞여 있으면 APPLICATION_400을 던진다")
	void rejectsSubmitWithInvalidMultiChoiceValue() {
		Posting posting = recruitingPostingWithQuestion(QuestionType.MULTI_CHOICE, true, null,
			new QuestionOptionsInput(List.of("A", "B", "C"), null, null, null));
		Position position = onlyPosition(posting);
		Long questionId = onlyQuestionId(posting);
		applicant.verifySchool(school);
		userRepository.save(applicant);
		Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(new AnswerInput(questionId, "[\"A\",\"없는값\"]", null)), null));

		assertThatThrownBy(() -> applicationService.submit(applicant.getId(), application.getId()))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(ApplicationErrorCode.INVALID_INPUT);
				assertThat(e.getFieldErrors()).extracting(FieldErrorResponse::message).containsExactly("선택지에 없는 값입니다.");
			});
	}

	@Test
	@DisplayName("MULTI_CHOICE 문항에 문자열이 아닌 값이 섞여 있으면 APPLICATION_400을 던진다")
	void rejectsSubmitWithNonStringMultiChoiceValue() {
		Posting posting = recruitingPostingWithQuestion(QuestionType.MULTI_CHOICE, true, null,
			new QuestionOptionsInput(List.of("A", "B", "C"), null, null, null));
		Position position = onlyPosition(posting);
		Long questionId = onlyQuestionId(posting);
		applicant.verifySchool(school);
		userRepository.save(applicant);
		Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(new AnswerInput(questionId, "[1]", null)), null));

		assertThatThrownBy(() -> applicationService.submit(applicant.getId(), application.getId()))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(ApplicationErrorCode.INVALID_INPUT);
				assertThat(e.getFieldErrors()).extracting(FieldErrorResponse::message).containsExactly("선택지에 없는 값입니다.");
			});
	}

	@Test
	@DisplayName("필수 MULTI_CHOICE 문항에 빈 배열을 제출하면 APPLICATION_400(필수 문항입니다.)을 던진다")
	void rejectsSubmitWithRequiredMultiChoiceEmptySelection() {
		Posting posting = recruitingPostingWithQuestion(QuestionType.MULTI_CHOICE, true, null,
			new QuestionOptionsInput(List.of("A", "B", "C"), null, null, null));
		Position position = onlyPosition(posting);
		Long questionId = onlyQuestionId(posting);
		applicant.verifySchool(school);
		userRepository.save(applicant);
		Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(new AnswerInput(questionId, "[]", null)), null));

		assertThatThrownBy(() -> applicationService.submit(applicant.getId(), application.getId()))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(ApplicationErrorCode.INVALID_INPUT);
				assertThat(e.getFieldErrors()).extracting(FieldErrorResponse::message).containsExactly("필수 문항입니다.");
			});
	}

	@Test
	@DisplayName("선택 MULTI_CHOICE 문항에 빈 배열을 제출하면 성공한다")
	void submitsSuccessfullyWithOptionalMultiChoiceEmptySelection() {
		Posting posting = recruitingPostingWithQuestion(QuestionType.MULTI_CHOICE, false, null,
			new QuestionOptionsInput(List.of("A", "B", "C"), null, null, null));
		Position position = onlyPosition(posting);
		Long questionId = onlyQuestionId(posting);
		applicant.verifySchool(school);
		userRepository.save(applicant);
		Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(new AnswerInput(questionId, "[]", null)), null));

		Application submitted = applicationService.submit(applicant.getId(), application.getId());

		assertThat(submitted.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
	}

	@Test
	@DisplayName("MULTI_CHOICE 문항에 JSON null 리터럴을 제출하면 APPLICATION_400(선택지에 없는 값입니다.)을 던진다")
	void rejectsSubmitWithNullLiteralMultiChoiceContent() {
		Posting posting = recruitingPostingWithQuestion(QuestionType.MULTI_CHOICE, true, null,
			new QuestionOptionsInput(List.of("A", "B", "C"), null, null, null));
		Position position = onlyPosition(posting);
		Long questionId = onlyQuestionId(posting);
		applicant.verifySchool(school);
		userRepository.save(applicant);
		Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(new AnswerInput(questionId, "null", null)), null));

		assertThatThrownBy(() -> applicationService.submit(applicant.getId(), application.getId()))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(ApplicationErrorCode.INVALID_INPUT);
				assertThat(e.getFieldErrors()).extracting(FieldErrorResponse::message).containsExactly("선택지에 없는 값입니다.");
			});
	}

	@Test
	@DisplayName("TEXT 문항에 maxLength보다 긴 답변을 제출하면 APPLICATION_400(글자 수 제한을 넘었습니다.)을 던진다")
	void rejectsSubmitWhenTextExceedsMaxLength() {
		Posting posting = recruitingPostingWithQuestion(QuestionType.TEXT, false, 5, null);
		Position position = onlyPosition(posting);
		Long questionId = onlyQuestionId(posting);
		applicant.verifySchool(school);
		userRepository.save(applicant);
		Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(new AnswerInput(questionId, "123456", null)), null));

		assertThatThrownBy(() -> applicationService.submit(applicant.getId(), application.getId()))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(ApplicationErrorCode.INVALID_INPUT);
				assertThat(e.getFieldErrors()).extracting(FieldErrorResponse::message).containsExactly("글자 수 제한을 넘었습니다.");
			});
	}

	@Test
	@DisplayName("TABLE 문항에 JSON 배열이 아닌 값을 제출하면 APPLICATION_400(표 형식이 올바르지 않습니다.)을 던진다")
	void rejectsSubmitWithMalformedTable() {
		Posting posting = recruitingPostingWithQuestion(QuestionType.TABLE, true, null,
			new QuestionOptionsInput(null, List.of("이름", "역할"), 3, null));
		Position position = onlyPosition(posting);
		Long questionId = onlyQuestionId(posting);
		applicant.verifySchool(school);
		userRepository.save(applicant);
		Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(new AnswerInput(questionId, "abc", null)), null));

		assertThatThrownBy(() -> applicationService.submit(applicant.getId(), application.getId()))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(ApplicationErrorCode.INVALID_INPUT);
				assertThat(e.getFieldErrors()).extracting(FieldErrorResponse::message).containsExactly("표 형식이 올바르지 않습니다.");
			});
	}

	@Test
	@DisplayName("TABLE 문항에 maxRows보다 많은 행을 제출하면 APPLICATION_400(행 수 제한을 넘었습니다.)을 던진다")
	void rejectsSubmitWhenTableExceedsMaxRows() {
		Posting posting = recruitingPostingWithQuestion(QuestionType.TABLE, true, null,
			new QuestionOptionsInput(null, List.of("이름", "역할"), 3, null));
		Position position = onlyPosition(posting);
		Long questionId = onlyQuestionId(posting);
		applicant.verifySchool(school);
		userRepository.save(applicant);
		String tooManyRows = "[[\"a\",\"1\"],[\"b\",\"2\"],[\"c\",\"3\"],[\"d\",\"4\"]]";
		Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(new AnswerInput(questionId, tooManyRows, null)), null));

		assertThatThrownBy(() -> applicationService.submit(applicant.getId(), application.getId()))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(ApplicationErrorCode.INVALID_INPUT);
				assertThat(e.getFieldErrors()).extracting(FieldErrorResponse::message).containsExactly("행 수 제한을 넘었습니다.");
			});
	}

	@Test
	@DisplayName("TABLE 문항에 열 개수가 다른 행을 제출하면 APPLICATION_400(열 구성이 문항과 다릅니다.)을 던진다")
	void rejectsSubmitWhenTableColumnCountMismatches() {
		Posting posting = recruitingPostingWithQuestion(QuestionType.TABLE, true, null,
			new QuestionOptionsInput(null, List.of("이름", "역할"), 3, null));
		Position position = onlyPosition(posting);
		Long questionId = onlyQuestionId(posting);
		applicant.verifySchool(school);
		userRepository.save(applicant);
		String wrongColumnCount = "[[\"a\",\"1\",\"여분\"]]";
		Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(new AnswerInput(questionId, wrongColumnCount, null)), null));

		assertThatThrownBy(() -> applicationService.submit(applicant.getId(), application.getId()))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(ApplicationErrorCode.INVALID_INPUT);
				assertThat(e.getFieldErrors()).extracting(FieldErrorResponse::message).containsExactly("열 구성이 문항과 다릅니다.");
			});
	}

	@Test
	@DisplayName("필수 TABLE 문항에 빈 배열을 제출하면 APPLICATION_400(필수 문항입니다.)을 던진다")
	void rejectsSubmitWithRequiredTableEmptyRows() {
		Posting posting = recruitingPostingWithQuestion(QuestionType.TABLE, true, null,
			new QuestionOptionsInput(null, List.of("이름", "역할"), 3, null));
		Position position = onlyPosition(posting);
		Long questionId = onlyQuestionId(posting);
		applicant.verifySchool(school);
		userRepository.save(applicant);
		Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(new AnswerInput(questionId, "[]", null)), null));

		assertThatThrownBy(() -> applicationService.submit(applicant.getId(), application.getId()))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(ApplicationErrorCode.INVALID_INPUT);
				assertThat(e.getFieldErrors()).extracting(FieldErrorResponse::message).containsExactly("필수 문항입니다.");
			});
	}

	@Test
	@DisplayName("TABLE 문항에 제약을 지키는 값을 제출하면 성공한다")
	void submitsSuccessfullyWithValidTable() {
		Posting posting = recruitingPostingWithQuestion(QuestionType.TABLE, true, null,
			new QuestionOptionsInput(null, List.of("이름", "역할"), 3, null));
		Position position = onlyPosition(posting);
		Long questionId = onlyQuestionId(posting);
		applicant.verifySchool(school);
		userRepository.save(applicant);
		String validTable = "[[\"a\",\"1\"],[\"b\",\"2\"]]";
		Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(new AnswerInput(questionId, validTable, null)), null));

		Application submitted = applicationService.submit(applicant.getId(), application.getId());

		assertThat(submitted.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
	}

	@Test
	@DisplayName("필수 FILE_LINK 문항에 첨부 없이 제출하면 APPLICATION_400(필수 첨부입니다.)을 던진다")
	void rejectsSubmitWithRequiredFileLinkMissingAttachment() {
		Posting posting = recruitingPostingWithQuestion(QuestionType.FILE_LINK, true, null,
			new QuestionOptionsInput(null, null, null, 5));
		Position position = onlyPosition(posting);
		applicant.verifySchool(school);
		userRepository.save(applicant);
		Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(), null));

		assertThatThrownBy(() -> applicationService.submit(applicant.getId(), application.getId()))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(ApplicationErrorCode.INVALID_INPUT);
				assertThat(e.getFieldErrors()).extracting(FieldErrorResponse::message).containsExactly("필수 첨부입니다.");
			});
	}

	@Test
	@DisplayName("maxFiles가 2인 문항에 첨부 3개로 제출하면 APPLICATION_400(첨부 개수 제한을 넘었습니다.)을 던진다")
	void rejectsSubmitWhenAttachmentCountExceedsMaxFiles() {
		Posting posting = recruitingPostingWithQuestion(QuestionType.FILE_LINK, false, null,
			new QuestionOptionsInput(null, null, null, 2));
		Position position = onlyPosition(posting);
		Long questionId = onlyQuestionId(posting);
		applicant.verifySchool(school);
		userRepository.save(applicant);
		List<AttachmentInput> threeAttachments = List.of(
			new AttachmentInput(AttachmentType.LINK, "https://a.com", null),
			new AttachmentInput(AttachmentType.LINK, "https://b.com", null),
			new AttachmentInput(AttachmentType.LINK, "https://c.com", null));
		Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(new AnswerInput(questionId, null, threeAttachments)), null));

		assertThatThrownBy(() -> applicationService.submit(applicant.getId(), application.getId()))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(ApplicationErrorCode.INVALID_INPUT);
				assertThat(e.getFieldErrors()).extracting(FieldErrorResponse::message).containsExactly("첨부 개수 제한을 넘었습니다.");
			});
	}

	@Test
	@DisplayName("첨부 2개로 저장한 답변을 첨부 1개로 다시 저장하면 첨부가 1개만 남는다")
	void keepsOnlyLatestAttachmentsOnResave() {
		Posting posting = recruitingPostingWithQuestion(QuestionType.FILE_LINK, true, null,
			new QuestionOptionsInput(null, null, null, 5));
		Position position = onlyPosition(posting);
		Long questionId = onlyQuestionId(posting);
		List<AttachmentInput> twoAttachments = List.of(
			new AttachmentInput(AttachmentType.LINK, "https://a.com", null),
			new AttachmentInput(AttachmentType.LINK, "https://b.com", null));

		Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(new AnswerInput(questionId, null, twoAttachments)), null));
		assertThat(attachmentRepository.findAllByAnswerInOrderById(
			answerRepository.findAllByApplicationOrderById(application))).hasSize(2);

		applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(),
				List.of(new AnswerInput(questionId, null, List.of(twoAttachments.get(0)))), null));

		assertThat(attachmentRepository.findAllByAnswerInOrderById(
			answerRepository.findAllByApplicationOrderById(application))).hasSize(1);
	}

	@Test
	@DisplayName("FILE_LINK가 아닌 문항에 첨부를 보내면 COMMON_400을 던진다")
	void rejectsAttachmentsForNonFileLinkQuestion() {
		Posting posting = recruitingPostingWithQuestion(QuestionType.TEXT, false, 500, null);
		Position position = onlyPosition(posting);
		Long questionId = onlyQuestionId(posting);
		List<AttachmentInput> attachments = List.of(new AttachmentInput(AttachmentType.LINK, "https://a.com", null));

		assertThatThrownBy(() -> applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(new AnswerInput(questionId, "답변", attachments)), null)))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
				assertThat(e.getReason()).isEqualTo(ApplicationInputReason.ATTACHMENT_NOT_ALLOWED_FOR_QUESTION_TYPE.name());
			});
	}

	@Test
	@DisplayName("LINK 첨부가 http/https가 아니면 COMMON_400을 던진다")
	void rejectsNonHttpLinkAttachment() {
		Posting posting = recruitingPostingWithQuestion(QuestionType.FILE_LINK, true, null,
			new QuestionOptionsInput(null, null, null, 5));
		Position position = onlyPosition(posting);
		Long questionId = onlyQuestionId(posting);
		List<AttachmentInput> attachments = List.of(
			new AttachmentInput(AttachmentType.LINK, "javascript:alert(1)", null));

		assertThatThrownBy(() -> applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(new AnswerInput(questionId, null, attachments)), null)))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
				assertThat(e.getReason()).isEqualTo(ApplicationInputReason.INVALID_ATTACHMENT_URL.name());
			});
	}

	@Test
	@DisplayName("FILE 첨부가 presigned로 올린 application-attachment 경로가 아니면 COMMON_400을 던진다")
	void rejectsFileAttachmentOutsideAttachmentPrefix() {
		Posting posting = recruitingPostingWithQuestion(QuestionType.FILE_LINK, true, null,
			new QuestionOptionsInput(null, null, null, 5));
		Position position = onlyPosition(posting);
		Long questionId = onlyQuestionId(posting);
		List<AttachmentInput> attachments = List.of(
			new AttachmentInput(AttachmentType.FILE, "https://아무-사이트.com/x.exe", null));

		assertThatThrownBy(() -> applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(new AnswerInput(questionId, null, attachments)), null)))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
				assertThat(e.getReason()).isEqualTo(ApplicationInputReason.INVALID_ATTACHMENT_URL.name());
			});
	}

	@Test
	@DisplayName("FILE 첨부가 application-attachment 경로이고 LINK 첨부가 https면 정상 저장된다")
	void savesValidLinkAndFileAttachments() {
		Posting posting = recruitingPostingWithQuestion(QuestionType.FILE_LINK, true, null,
			new QuestionOptionsInput(null, null, null, 5));
		Position position = onlyPosition(posting);
		Long questionId = onlyQuestionId(posting);
		List<AttachmentInput> attachments = List.of(
			new AttachmentInput(AttachmentType.LINK, "https://a.com", null),
			new AttachmentInput(AttachmentType.FILE,
				"https://cdn.mozip.kr/application-attachment/2026/02/uuid.pdf", "결과.pdf"));

		Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(new AnswerInput(questionId, null, attachments)), null));

		assertThat(attachmentRepository.findAllByAnswerInOrderById(
			answerRepository.findAllByApplicationOrderById(application))).hasSize(2);
	}

	@Test
	@DisplayName("포지션 있는 공고에 positionId 없이 제출하면 APPLICATION_400(position)을 던진다")
	void rejectsSubmitWithoutPositionWhenPostingHasPositions() {
		Posting posting = recruitingPostingWithPosition();
		Long questionId = onlyQuestionId(posting);
		applicant.verifySchool(school);
		userRepository.save(applicant);
		Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(null, List.of(new AnswerInput(questionId, "답변", null)), null));

		assertThatThrownBy(() -> applicationService.submit(applicant.getId(), application.getId()))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(ApplicationErrorCode.INVALID_INPUT);
				assertThat(e.getFieldErrors()).extracting(FieldErrorResponse::field).contains("position");
			});
	}

	@Test
	@DisplayName("다른 회원의 지원서를 제출하려 하면 COMMON_403을 던진다")
	void rejectsSubmittingByNonOwner() {
		Posting posting = recruitingPostingWithPosition();
		Position position = onlyPosition(posting);
		Long questionId = onlyQuestionId(posting);
		applicant.verifySchool(school);
		userRepository.save(applicant);
		Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(new AnswerInput(questionId, "답변", null)), null));
		User stranger = userRepository.save(User.create("01044440099", "encoded", "타인", false, LocalDateTime.now()));

		assertThatThrownBy(() -> applicationService.submit(stranger.getId(), application.getId()))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(CommonErrorCode.FORBIDDEN);
	}

	@Test
	@DisplayName("본인은 DRAFT도 조회 가능하고, 운영진은 제출된 뒤에만 조회 가능하며, 그 외는 COMMON_403을 던진다")
	void rejectsViewingByOthers() {
		Posting posting = recruitingPostingWithPosition();
		Position position = onlyPosition(posting);
		Long questionId = onlyQuestionId(posting);
		applicant.verifySchool(school);
		userRepository.save(applicant);
		Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(new AnswerInput(questionId, "답변", null)), null));
		User stranger = userRepository.save(User.create("01044440003", "encoded", "타인", false, LocalDateTime.now()));

		assertThat(applicationService.findDetail(application.getId(), applicant.getId()).getId())
			.isEqualTo(application.getId());
		assertThatThrownBy(() -> applicationService.findDetail(application.getId(), operator.getId()))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(CommonErrorCode.FORBIDDEN);
		assertThatThrownBy(() -> applicationService.findDetail(application.getId(), stranger.getId()))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(CommonErrorCode.FORBIDDEN);

		applicationService.submit(applicant.getId(), application.getId());

		assertThat(applicationService.findDetail(application.getId(), operator.getId()).getId())
			.isEqualTo(application.getId());
	}

	@Test
	@DisplayName("getSubmittedApplications는 그 공고의 제출된 지원서만 돌려준다")
	void getSubmittedApplicationsReturnsOnlySubmitted() {
		Posting posting = recruitingPostingWithPosition();
		Position position = onlyPosition(posting);
		Long questionId = onlyQuestionId(posting);
		applicant.verifySchool(school);
		userRepository.save(applicant);
		Application draftOnly = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(new AnswerInput(questionId, "답변1", null)), null));
		User another = userRepository.save(User.create("01044440004", "encoded", "지원자2", false, LocalDateTime.now()));
		another.verifySchool(school);
		userRepository.save(another);
		Application submitted = applicationService.saveDraft(another.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(new AnswerInput(questionId, "답변2", null)), null));
		applicationService.submit(another.getId(), submitted.getId());

		List<Application> result = applicationService.getSubmittedApplications(posting.getId());

		assertThat(result).extracting(Application::getId).containsExactly(submitted.getId());
		assertThat(draftOnly.getStatus()).isEqualTo(ApplicationStatus.DRAFT);
	}

	private Posting recruitingPostingWithPosition() {
		Posting posting = postingService.saveDraft(operator.getId(), organization.getId(), null,
			basicInput("모집 공고 " + System.nanoTime()));
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withPositions(List.of(new PositionInput(null, "백엔드", 1, 0))));
		Long positionId = positionRepository.findAllByPostingOrderBySortOrder(posting).get(0).getId();
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withQuestions(List.of(new QuestionInput(null, QuestionType.TEXT, "자기소개", null, true, 500, null, 0))));
		postingService.publish(operator.getId(), posting.getId());
		return posting;
	}

	private Posting recruitingPostingWithQuestion(QuestionType type, boolean required, Integer maxLength,
		QuestionOptionsInput options) {
		Posting posting = postingService.saveDraft(operator.getId(), organization.getId(), null,
			basicInput("문항 테스트 공고 " + System.nanoTime()));
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withPositions(List.of(new PositionInput(null, "백엔드", 1, 0))));
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withQuestions(List.of(new QuestionInput(null, type, "문항", null, required, maxLength, options, 0))));
		postingService.publish(operator.getId(), posting.getId());
		return posting;
	}

	private Posting recruitingPostingWithInterview(LocalDate date, LocalTime startTime, LocalTime endTime) {
		Posting posting = postingService.saveDraft(operator.getId(), organization.getId(), null,
			basicInput("면접 공고 " + System.nanoTime()));
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withPositions(List.of(new PositionInput(null, "백엔드", 1, 0))));
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withInterview(true, 20, 10, AssignRule.ALL_MEMBERS, 1, 1,
				List.of(new InterviewTimeRangeInput(null, date, startTime, endTime, List.of()))));
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withQuestions(List.of(new QuestionInput(null, QuestionType.TEXT, "자기소개", null, true, 500, null, 0))));
		postingService.publish(operator.getId(), posting.getId());
		return posting;
	}

	private PostingDraftInput withInterview(Boolean hasInterview,
		Integer interviewMinutes, Integer breakMinutes, AssignRule assignRule, Integer maxApplicantsPerInterview,
		Integer maxParallelInterviews, List<InterviewTimeRangeInput> interviewTimeRanges) {
		return new PostingDraftInput(null, null, null, null, null, null, null, null,
			null, null, null, null, null,
			null, null,
			hasInterview, interviewMinutes, breakMinutes, assignRule, maxApplicantsPerInterview,
			maxParallelInterviews, interviewTimeRanges,
			null, null);
	}

	private Position onlyPosition(Posting posting) {
		return positionRepository.findAllByPostingOrderBySortOrder(posting).get(0);
	}

	private Long onlyQuestionId(Posting posting) {
		return questionRepository.findAllByPostingOrderBySortOrder(posting).get(0).getId();
	}

	private PostingDraftInput basicInput(String title) {
		return basicInput(title, LocalDateTime.now().plusDays(7));
	}

	private PostingDraftInput basicInput(String title, LocalDateTime recruitEndAt) {
		return new PostingDraftInput(title, "본문", ArgumentValue.ofNullable("2026-2"),
			ArgumentValue.ofNullable("서울"), ArgumentValue.ofNullable("무료"), true, false, null,
			null, null, LocalDateTime.now(), recruitEndAt, null,
			null, null,
			null, null, null, null, null, null, null,
			null, null);
	}

	private PostingDraftInput basicInputWithWindow(String title, LocalDateTime recruitStartAt,
		LocalDateTime recruitEndAt) {
		return new PostingDraftInput(title, "본문", ArgumentValue.ofNullable("2026-2"),
			ArgumentValue.ofNullable("서울"), ArgumentValue.ofNullable("무료"), true, false, null,
			null, null, recruitStartAt, recruitEndAt, null,
			null, null,
			null, null, null, null, null, null, null,
			null, null);
	}

	private PostingDraftInput withPositions(List<PositionInput> positions) {
		return new PostingDraftInput(null, null, null, null, null, null, null, null,
			null, null, null, null, null,
			false, positions,
			null, null, null, null, null, null, null,
			null, null);
	}

	private PostingDraftInput withQuestions(List<QuestionInput> questions) {
		return new PostingDraftInput(null, null, null, null, null, null, null, null,
			null, null, null, null, null,
			null, null,
			null, null, null, null, null, null, null,
			true, questions);
	}

}
