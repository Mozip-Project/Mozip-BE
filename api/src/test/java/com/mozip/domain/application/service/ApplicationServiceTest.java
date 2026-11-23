package com.mozip.domain.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.TestcontainersConfiguration;
import com.mozip.domain.application.dto.AnswerInput;
import com.mozip.domain.application.dto.ApplicationInput;
import com.mozip.domain.application.entity.Answer;
import com.mozip.domain.application.entity.Application;
import com.mozip.domain.application.entity.ApplicationStatus;
import com.mozip.domain.application.exception.ApplicationConflictReason;
import com.mozip.domain.application.exception.ApplicationErrorCode;
import com.mozip.domain.application.exception.ApplicationForbiddenReason;
import com.mozip.domain.application.repository.AnswerRepository;
import com.mozip.domain.application.repository.ApplicationRepository;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.OrganizationScope;
import com.mozip.domain.organization.entity.OrganizationType;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.repository.OrganizationRepository;
import com.mozip.domain.posting.dto.PositionInput;
import com.mozip.domain.posting.dto.PostingDraftInput;
import com.mozip.domain.posting.dto.QuestionInput;
import com.mozip.domain.posting.dto.QuestionOptionsInput;
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
	@DisplayName("다른 공고의 문항으로 답변하면 COMMON_400을 던진다")
	void rejectsAnswerForQuestionFromAnotherPosting() {
		Posting posting = recruitingPostingWithPosition();
		Position position = onlyPosition(posting);
		Posting otherPosting = recruitingPostingWithPosition();
		Long otherQuestionId = onlyQuestionId(otherPosting);

		assertThatThrownBy(() -> applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(new AnswerInput(otherQuestionId, "답변", null)), null)))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(CommonErrorCode.INVALID_INPUT);
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
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(CommonErrorCode.INVALID_INPUT);
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
		Posting posting = postingService.saveDraft(operator.getId(), organization.getId(), null,
			basicInput("마감 지난 공고 " + System.nanoTime(), LocalDateTime.now().minusDays(1)));
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withPositions(List.of(new PositionInput(null, "백엔드", 1, 0))));
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withQuestions(List.of(new QuestionInput(null, QuestionType.TEXT, "자기소개", null, true, 500, null, 0))));
		postingService.publish(operator.getId(), posting.getId());

		assertThatThrownBy(() -> applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(null, null, null)))
			.isInstanceOf(BusinessException.class)
			.satisfies(e -> {
				assertThat(((BusinessException)e).getErrorCode()).isEqualTo(PostingErrorCode.CONFLICT);
				assertThat(((BusinessException)e).getReason()).isEqualTo(PostingConflictReason.NOT_RECRUITING.name());
			});
	}

	@Test
	@DisplayName("필수 문항에 답하지 않고 제출하면 APPLICATION_400을 던지고 fieldErrors에 questionId가 담긴다")
	void rejectsSubmitWithMissingRequiredAnswer() {
		Posting posting = recruitingPostingWithPosition();
		Position position = onlyPosition(posting);
		applicant.verifySchool(school);
		userRepository.save(applicant);

		Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(), null));

		assertThatThrownBy(() -> applicationService.submit(applicant.getId(), application.getId()))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(ApplicationErrorCode.INVALID_INPUT);
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
	@DisplayName("본인 또는 그 공고 단체 운영진이 아니면 조회 시 COMMON_403을 던진다")
	void rejectsViewingByOthers() {
		Posting posting = recruitingPostingWithPosition();
		Position position = onlyPosition(posting);
		Long questionId = onlyQuestionId(posting);
		Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(position.getId(), List.of(new AnswerInput(questionId, "답변", null)), null));
		User stranger = userRepository.save(User.create("01044440003", "encoded", "타인", false, LocalDateTime.now()));

		assertThat(applicationService.findDetail(application.getId(), applicant.getId()).getId())
			.isEqualTo(application.getId());
		assertThat(applicationService.findDetail(application.getId(), operator.getId()).getId())
			.isEqualTo(application.getId());
		assertThatThrownBy(() -> applicationService.findDetail(application.getId(), stranger.getId()))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(CommonErrorCode.FORBIDDEN);
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
		return new PostingDraftInput(title, "본문", "2026-2", "서울", "무료", true, false, null,
			null, null, LocalDateTime.now(), recruitEndAt, null,
			null, null,
			null, null, null, null, null, null, null, null,
			null, null,
			null);
	}

	private PostingDraftInput withPositions(List<PositionInput> positions) {
		return new PostingDraftInput(null, null, null, null, null, null, null, null,
			null, null, null, null, null,
			false, positions,
			null, null, null, null, null, null, null, null,
			null, null,
			null);
	}

	private PostingDraftInput withQuestions(List<QuestionInput> questions) {
		return new PostingDraftInput(null, null, null, null, null, null, null, null,
			null, null, null, null, null,
			null, null,
			null, null, null, null, null, null, null, null,
			true, questions,
			null);
	}

}
