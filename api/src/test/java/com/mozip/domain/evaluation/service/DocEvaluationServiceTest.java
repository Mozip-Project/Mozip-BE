package com.mozip.domain.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.TestcontainersConfiguration;
import com.mozip.domain.application.dto.ApplicationInput;
import com.mozip.domain.application.entity.Application;
import com.mozip.domain.application.service.ApplicationService;
import com.mozip.domain.evaluation.dto.ApplicationSort;
import com.mozip.domain.evaluation.dto.DocEvaluationApplicationPage;
import com.mozip.domain.evaluation.dto.DocEvaluationApplicationRow;
import com.mozip.domain.evaluation.dto.DocEvaluationTab;
import com.mozip.domain.evaluation.dto.DocScoreInput;
import com.mozip.domain.evaluation.dto.MyDocEvaluationStatus;
import com.mozip.domain.evaluation.dto.PositionCount;
import com.mozip.domain.evaluation.dto.SaveDocEvaluationInput;
import com.mozip.domain.evaluation.dto.SaveDocEvaluationPayload;
import com.mozip.domain.evaluation.entity.DocEvaluation;
import com.mozip.domain.evaluation.entity.DocEvaluationStatus;
import com.mozip.domain.evaluation.entity.DocScore;
import com.mozip.domain.evaluation.entity.Opinion;
import com.mozip.domain.evaluation.exception.DocEvaluationInputReason;
import com.mozip.domain.evaluation.exception.EvaluationErrorCode;
import com.mozip.domain.evaluation.repository.DocEvaluationRepository;
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
import com.mozip.domain.posting.entity.Position;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.entity.Question;
import com.mozip.domain.posting.entity.QuestionType;
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
import com.mozip.global.exception.ErrorCode;

import jakarta.persistence.EntityManager;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class DocEvaluationServiceTest {

	@Autowired
	private DocEvaluationService docEvaluationService;

	@Autowired
	private DocEvaluationRepository docEvaluationRepository;

	@Autowired
	private PostingService postingService;

	@Autowired
	private ApplicationService applicationService;

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

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private Organization organization;
	private User operator;
	private User otherOperator;
	private User stranger;
	private School school;
	private int phoneSequence;

	// 포지션 2개(백엔드, 프론트)와 문항 3개(공통, 백엔드용, 프론트용)를 가진 모집 중 공고
	private Posting posting;
	private Position backend;
	private Position frontend;
	private Question commonQuestion;
	private Question backendQuestion;
	private Question frontendQuestion;

	@BeforeEach
	void setUp() {
		organization = organizationRepository.save(
			Organization.create("테스트 단체", OrganizationType.CLUB, null, OrganizationScope.CAMPUS, null, true, null,
				null));
		operator = saveUser("운영진");
		memberRepository.save(Member.create(organization, operator, null, MemberRole.REPRESENTATIVE));
		otherOperator = saveUser("다른 운영진");
		memberRepository.save(Member.create(organization, otherOperator, null, MemberRole.OPERATOR));
		stranger = saveUser("남");
		school = schoolRepository.save(School.create("성균관대학교", "skku.edu"));

		posting = postingService.saveDraft(operator.getId(), organization.getId(), null, basicInput("평가 공고"));
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withPositions(List.of(new PositionInput(null, "백엔드", 2, 0), new PositionInput(null, "프론트", 2, 1))));
		List<Position> positions = positionRepository.findAllByPostingOrderBySortOrder(posting);
		backend = positions.get(0);
		frontend = positions.get(1);
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(), withQuestions(true, List.of(
			new QuestionInput(null, QuestionType.TEXT, "지원 동기", null, false, 500, null, 0),
			new QuestionInput(backend.getId(), QuestionType.TEXT, "백엔드 경험", null, false, 500, null, 1),
			new QuestionInput(frontend.getId(), QuestionType.TEXT, "프론트 경험", null, false, 500, null, 2))));
		List<Question> questions = questionRepository.findAllByPostingOrderBySortOrder(posting);
		commonQuestion = questions.get(0);
		backendQuestion = questions.get(1);
		frontendQuestion = questions.get(2);
		postingService.publish(operator.getId(), posting.getId());
	}

	@Test
	@DisplayName("공고 단체의 운영진이 아니면 목록, 칩, 내 평가 조회, 저장이 모두 COMMON_403이다")
	void rejectsNonOperator() {
		Application application = submitApplication("김지원", backend);

		assertBusinessError(() -> docEvaluationService.findApplications(stranger.getId(), posting.getId(),
			DocEvaluationTab.ALL, null, null, ApplicationSort.SUBMITTED_DESC, 1), CommonErrorCode.FORBIDDEN, null);
		assertBusinessError(() -> docEvaluationService.findApplicationChips(stranger.getId(), posting.getId(),
			DocEvaluationTab.ALL, null, null, ApplicationSort.SUBMITTED_DESC), CommonErrorCode.FORBIDDEN, null);
		assertBusinessError(() -> docEvaluationService.findMyEvaluation(stranger.getId(), application.getId()),
			CommonErrorCode.FORBIDDEN, null);
		assertBusinessError(() -> docEvaluationService.save(stranger.getId(),
			input(application, Opinion.PASS, backendScores(3, 3), null), true), CommonErrorCode.FORBIDDEN, null);
		assertThat(docEvaluationRepository.findAll()).isEmpty();
	}

	@Test
	@DisplayName("없는 공고의 목록은 POSTING_404, 없거나 제출 전인 지원서의 평가 조회와 저장은 EVALUATION_404다")
	void notFound() {
		Application draft = applicationService.saveDraft(saveApplicant("임시저장").getId(), posting.getId(),
			new ApplicationInput(backend.getId(), List.of(), null));

		assertBusinessError(() -> docEvaluationService.findApplications(operator.getId(), -1L, null, null, null,
			null, null), PostingErrorCode.NOT_FOUND, null);
		assertBusinessError(() -> docEvaluationService.findApplicationChips(operator.getId(), -1L, null, null, null,
			null), PostingErrorCode.NOT_FOUND, null);
		assertBusinessError(() -> docEvaluationService.findMyEvaluation(operator.getId(), -1L),
			EvaluationErrorCode.APPLICATION_NOT_FOUND, null);
		assertBusinessError(() -> docEvaluationService.findMyEvaluation(operator.getId(), draft.getId()),
			EvaluationErrorCode.APPLICATION_NOT_FOUND, null);
		assertBusinessError(() -> docEvaluationService.save(operator.getId(),
			new SaveDocEvaluationInput(-1L, null, null, null), false), EvaluationErrorCode.APPLICATION_NOT_FOUND, null);
		assertBusinessError(() -> docEvaluationService.save(operator.getId(),
			new SaveDocEvaluationInput(draft.getId(), null, null, null), false),
			EvaluationErrorCode.APPLICATION_NOT_FOUND, null);
	}

	@Test
	@DisplayName("서류 결과가 확정된 뒤에는 자동 저장과 저장이 EVALUATION_423이고, 목록과 내 평가 조회는 그대로 된다")
	void rejectsSaveAfterResultConfirmed() {
		Application application = submitApplication("김지원", backend);
		docEvaluationService.save(operator.getId(), input(application, Opinion.PASS, backendScores(4, 5), "확정 전"),
			true);
		entityManager.flush();
		jdbcTemplate.update("update postings set doc_result_confirmed_at = now() where id = ?", posting.getId());
		entityManager.clear();

		assertBusinessError(() -> docEvaluationService.save(operator.getId(),
			input(application, Opinion.FAIL, backendScores(1, 1), "확정 후"), true),
			EvaluationErrorCode.RESULT_CONFIRMED, null);
		assertBusinessError(() -> docEvaluationService.save(operator.getId(),
			input(application, null, null, null), false), EvaluationErrorCode.RESULT_CONFIRMED, null);

		DocEvaluation evaluation = docEvaluationService.findMyEvaluation(operator.getId(), application.getId());
		assertThat(evaluation.getOpinion()).isEqualTo(Opinion.PASS);
		assertThat(evaluation.getMemo()).isEqualTo("확정 전");
		assertThat(page(DocEvaluationTab.ALL, null, null).totalCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("제출 전 지원서는 목록에 없고, 작성 중(DRAFT) 평가는 평가 전 탭에, 저장한 평가는 평가 완료 탭에 들어간다")
	void tabsAndCounts() {
		Application none = submitApplication("가", backend);
		Application drafted = submitApplication("나", backend);
		Application saved = submitApplication("다", frontend);
		applicationService.saveDraft(saveApplicant("임시저장").getId(), posting.getId(),
			new ApplicationInput(backend.getId(), List.of(), null));
		docEvaluationService.save(operator.getId(), input(drafted, Opinion.HOLD, null, "작성 중"), false);
		docEvaluationService.save(operator.getId(), input(saved, Opinion.PASS, frontendScores(5, 5), null), true);
		// 다른 운영진의 평가는 내 상태에 영향이 없음
		docEvaluationService.save(otherOperator.getId(), input(none, Opinion.FAIL, backendScores(1, 1), null), true);

		DocEvaluationApplicationPage all = page(DocEvaluationTab.ALL, null, null);
		assertThat(ids(all.rows())).containsExactly(saved.getId(), drafted.getId(), none.getId());
		assertThat(all.rows()).extracting(DocEvaluationApplicationRow::myEvaluationStatus)
			.containsExactly(MyDocEvaluationStatus.SAVED, MyDocEvaluationStatus.DRAFT, MyDocEvaluationStatus.NONE);
		assertThat(all.totalCount()).isEqualTo(3);
		assertThat(all.tabCounts().all()).isEqualTo(3);
		assertThat(all.tabCounts().notEvaluated()).isEqualTo(2);
		assertThat(all.tabCounts().evaluated()).isEqualTo(1);

		DocEvaluationApplicationPage notEvaluated = page(DocEvaluationTab.NOT_EVALUATED, null, null);
		assertThat(ids(notEvaluated.rows())).containsExactly(drafted.getId(), none.getId());
		assertThat(notEvaluated.totalCount()).isEqualTo(2);
		assertThat(notEvaluated.tabCounts().all()).isEqualTo(3);

		DocEvaluationApplicationPage evaluated = page(DocEvaluationTab.EVALUATED, null, null);
		assertThat(ids(evaluated.rows())).containsExactly(saved.getId());
		assertThat(evaluated.totalCount()).isEqualTo(1);

		DocEvaluationApplicationPage others = docEvaluationService.findApplications(otherOperator.getId(),
			posting.getId(), DocEvaluationTab.EVALUATED, null, null, null, null);
		assertThat(ids(others.rows())).containsExactly(none.getId());
	}

	@Test
	@DisplayName("totalCount는 탭, 포지션, 검색어를 모두, tabCounts는 포지션과 검색어를, positionCounts는 검색어만 적용한다")
	void countBasis() {
		Application backendKim = submitApplication("김하나", backend);
		Application backendLee = submitApplication("이하나", backend);
		Application frontendKim = submitApplication("김두리", frontend);
		submitApplication("박세찌", frontend);
		docEvaluationService.save(operator.getId(), input(backendKim, Opinion.PASS, backendScores(5, 5), null), true);
		docEvaluationService.save(operator.getId(), input(frontendKim, Opinion.PASS, frontendScores(5, 5), null),
			true);

		DocEvaluationApplicationPage result = docEvaluationService.findApplications(operator.getId(),
			posting.getId(), DocEvaluationTab.NOT_EVALUATED, List.of(backend.getId()), " 하나 ", null, 1);

		assertThat(ids(result.rows())).containsExactly(backendLee.getId());
		assertThat(result.totalCount()).isEqualTo(1);
		assertThat(result.tabCounts().all()).isEqualTo(2);
		assertThat(result.tabCounts().notEvaluated()).isEqualTo(1);
		assertThat(result.tabCounts().evaluated()).isEqualTo(1);
		assertThat(result.positionCounts()).extracting(count -> count.position().getId(), PositionCount::count)
			.containsExactly(tuple(backend.getId(), 2),
				tuple(frontend.getId(), 0));

		DocEvaluationApplicationPage kim = page(DocEvaluationTab.ALL, null, "김");
		assertThat(ids(kim.rows())).containsExactlyInAnyOrder(backendKim.getId(), frontendKim.getId());
		assertThat(kim.positionCounts()).extracting(PositionCount::count).containsExactly(1, 1);

		DocEvaluationApplicationPage both = page(DocEvaluationTab.ALL, List.of(backend.getId(), frontend.getId()),
			null);
		assertThat(both.totalCount()).isEqualTo(4);
		assertThat(page(DocEvaluationTab.ALL, List.of(), null).totalCount()).isEqualTo(4);
	}

	@Test
	@DisplayName("한 쪽에 10건씩 나오고 정렬은 제출일과 이름 기준으로 바뀐다")
	void paginationAndSort() {
		List<Application> applications = new ArrayList<>();
		for (int i = 0; i < 12; i++) {
			applications.add(submitApplication("지원자" + (char)('가' + i), i % 2 == 0 ? backend : frontend));
		}

		DocEvaluationApplicationPage first = docEvaluationService.findApplications(operator.getId(), posting.getId(),
			null, null, null, null, null);
		assertThat(first.page()).isEqualTo(1);
		assertThat(first.totalCount()).isEqualTo(12);
		assertThat(first.totalPages()).isEqualTo(2);
		assertThat(ids(first.rows())).containsExactlyElementsOf(
			applications.reversed().stream().limit(10).map(Application::getId).toList());

		DocEvaluationApplicationPage second = docEvaluationService.findApplications(operator.getId(),
			posting.getId(), DocEvaluationTab.ALL, null, null, ApplicationSort.SUBMITTED_DESC, 2);
		assertThat(ids(second.rows())).containsExactly(applications.get(1).getId(), applications.get(0).getId());
		assertThat(second.page()).isEqualTo(2);

		DocEvaluationApplicationPage beyond = docEvaluationService.findApplications(operator.getId(),
			posting.getId(), DocEvaluationTab.ALL, null, null, ApplicationSort.SUBMITTED_DESC, 3);
		assertThat(beyond.rows()).isEmpty();
		assertThat(beyond.totalCount()).isEqualTo(12);

		DocEvaluationApplicationPage nameDesc = docEvaluationService.findApplications(operator.getId(),
			posting.getId(), DocEvaluationTab.ALL, null, null, ApplicationSort.NAME_DESC, 1);
		assertThat(ids(nameDesc.rows())).containsExactlyElementsOf(
			applications.reversed().stream().limit(10).map(Application::getId).toList());

		List<DocEvaluationApplicationRow> chips = docEvaluationService.findApplicationChips(operator.getId(),
			posting.getId(), DocEvaluationTab.ALL, List.of(backend.getId()), null, ApplicationSort.NAME_ASC);
		assertThat(ids(chips)).containsExactly(applications.get(0).getId(), applications.get(2).getId(),
			applications.get(4).getId(), applications.get(6).getId(), applications.get(8).getId(),
			applications.get(10).getId());

		List<DocEvaluationApplicationRow> submittedAsc = docEvaluationService.findApplicationChips(operator.getId(),
			posting.getId(), null, null, null, ApplicationSort.SUBMITTED_ASC);
		assertThat(ids(submittedAsc)).containsExactlyElementsOf(
			applications.stream().map(Application::getId).toList());
	}

	@Test
	@DisplayName("page가 1보다 작거나 positionIds에 이 공고 포지션이 아닌 id가 있으면 COMMON_400이다")
	void rejectsInvalidListInput() {
		assertBusinessError(() -> docEvaluationService.findApplications(operator.getId(), posting.getId(),
			DocEvaluationTab.ALL, null, null, null, 0), CommonErrorCode.INVALID_INPUT, null);
		assertBusinessError(() -> docEvaluationService.findApplications(operator.getId(), posting.getId(),
			DocEvaluationTab.ALL, List.of(backend.getId(), -1L), null, null, 1), CommonErrorCode.INVALID_INPUT, null);
		assertBusinessError(() -> docEvaluationService.findApplicationChips(operator.getId(), posting.getId(),
			DocEvaluationTab.ALL, List.of(-1L), null, null), CommonErrorCode.INVALID_INPUT, null);
	}

	@Test
	@DisplayName("포지션 없는 모집이면 positionCounts는 빈 배열이고 지원서는 그대로 나온다")
	void noPositionPosting() {
		Posting noPosition = postingService.saveDraft(operator.getId(), organization.getId(), null,
			basicInput("포지션 없는 공고"));
		postingService.saveDraft(operator.getId(), organization.getId(), noPosition.getId(), withNoPosition());
		postingService.saveDraft(operator.getId(), organization.getId(), noPosition.getId(), withQuestions(false,
			List.of(new QuestionInput(null, QuestionType.TEXT, "자기소개", null, false, 500, null, 0))));
		postingService.publish(operator.getId(), noPosition.getId());
		Application application = submitApplication("김지원", noPosition, null);

		DocEvaluationApplicationPage result = docEvaluationService.findApplications(operator.getId(),
			noPosition.getId(), DocEvaluationTab.ALL, null, null, null, 1);

		assertThat(ids(result.rows())).containsExactly(application.getId());
		assertThat(result.positionCounts()).isEmpty();
	}

	@Test
	@DisplayName("자동 저장은 의견, 점수, 메모가 비어도 DRAFT로 저장되고 다시 저장하면 같은 행을 덮어쓴다")
	void autoSaveOverwrites() {
		Application application = submitApplication("김지원", backend);

		SaveDocEvaluationPayload empty = docEvaluationService.save(operator.getId(),
			input(application, null, null, null), false);

		assertThat(empty.evaluation().getStatus()).isEqualTo(DocEvaluationStatus.DRAFT);
		assertThat(empty.evaluation().getOpinion()).isNull();
		assertThat(empty.evaluation().getScores()).isEmpty();
		assertThat(empty.evaluation().getSubmittedAt()).isNull();
		assertThat(empty.evaluation().getUpdatedAt()).isNotNull();

		SaveDocEvaluationPayload partial = docEvaluationService.save(operator.getId(), input(application,
			Opinion.HOLD, List.of(new DocScoreInput(commonQuestion.getId(), 2)), "고민 중"), false);

		assertThat(partial.evaluation().getId()).isEqualTo(empty.evaluation().getId());
		assertThat(partial.evaluation().getStatus()).isEqualTo(DocEvaluationStatus.DRAFT);
		assertThat(docEvaluationRepository.findAll()).hasSize(1);

		docEvaluationService.save(operator.getId(), input(application, null, null, null), false);
		entityManager.flush();
		entityManager.clear();

		DocEvaluation cleared = docEvaluationService.findMyEvaluation(operator.getId(), application.getId());
		assertThat(cleared.getOpinion()).isNull();
		assertThat(cleared.getScores()).isEmpty();
		assertThat(cleared.getMemo()).isNull();
	}

	@Test
	@DisplayName("저장하면 SAVED가 되고 점수는 jsonb를 거쳐 그대로 읽히며, 그 뒤 자동 저장은 SAVED와 제출 시각을 유지한다")
	void submitThenAutoSaveKeepsSaved() {
		Application application = submitApplication("김지원", backend);

		SaveDocEvaluationPayload saved = docEvaluationService.save(operator.getId(),
			input(application, Opinion.PASS, backendScores(4, 5), "좋음"), true);
		assertThat(saved.evaluation().getSubmittedAt()).isNotNull();
		entityManager.flush();
		entityManager.clear();

		DocEvaluation found = docEvaluationService.findMyEvaluation(operator.getId(), application.getId());
		assertThat(found.getStatus()).isEqualTo(DocEvaluationStatus.SAVED);
		assertThat(found.getOpinion()).isEqualTo(Opinion.PASS);
		assertThat(found.getMemo()).isEqualTo("좋음");
		assertThat(found.getScores()).containsExactly(new DocScore(commonQuestion.getId(), 4),
			new DocScore(backendQuestion.getId(), 5));
		assertThat(found.getMember().getUser().getId()).isEqualTo(operator.getId());
		// DB는 마이크로초까지만 남기므로 다시 읽은 값과 비교
		LocalDateTime submittedAt = found.getSubmittedAt();

		docEvaluationService.save(operator.getId(),
			input(application, Opinion.HOLD, List.of(new DocScoreInput(commonQuestion.getId(), 1)), null), false);
		entityManager.flush();
		entityManager.clear();

		DocEvaluation afterAutoSave = docEvaluationService.findMyEvaluation(operator.getId(), application.getId());
		assertThat(afterAutoSave.getStatus()).isEqualTo(DocEvaluationStatus.SAVED);
		assertThat(afterAutoSave.getOpinion()).isEqualTo(Opinion.HOLD);
		assertThat(afterAutoSave.getScores()).containsExactly(new DocScore(commonQuestion.getId(), 1));
		assertThat(afterAutoSave.getSubmittedAt()).isEqualTo(submittedAt);
		assertThat(page(DocEvaluationTab.EVALUATED, null, null).totalCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("아직 평가를 시작하지 않은 지원서의 내 평가는 null이고, 다른 운영진의 평가는 내 평가로 나오지 않는다")
	void myEvaluationIsPerOperator() {
		Application application = submitApplication("김지원", backend);
		docEvaluationService.save(otherOperator.getId(), input(application, Opinion.FAIL, backendScores(1, 2), null),
			true);

		assertThat(docEvaluationService.findMyEvaluation(operator.getId(), application.getId())).isNull();
		assertThat(docEvaluationService.findMyEvaluation(otherOperator.getId(), application.getId()).getOpinion())
			.isEqualTo(Opinion.FAIL);
	}

	@Test
	@DisplayName("저장 버튼인데 의견이 없으면 OPINION_REQUIRED, 점수를 안 매긴 문항이 있으면 SCORE_REQUIRED다")
	void requiresOpinionAndScoresOnSubmit() {
		Application application = submitApplication("김지원", backend);

		assertBusinessError(() -> docEvaluationService.save(operator.getId(),
			input(application, null, backendScores(3, 3), null), true),
			CommonErrorCode.INVALID_INPUT, DocEvaluationInputReason.OPINION_REQUIRED);
		assertBusinessError(() -> docEvaluationService.save(operator.getId(),
			input(application, Opinion.PASS, List.of(new DocScoreInput(commonQuestion.getId(), 3)), null), true),
			CommonErrorCode.INVALID_INPUT, DocEvaluationInputReason.SCORE_REQUIRED);
		assertBusinessError(() -> docEvaluationService.save(operator.getId(),
			input(application, Opinion.PASS, null, null), true),
			CommonErrorCode.INVALID_INPUT, DocEvaluationInputReason.SCORE_REQUIRED);
		assertThat(docEvaluationRepository.findAll()).isEmpty();
	}

	@Test
	@DisplayName("점수가 1~5점이 아니면 INVALID_SCORE, 메모가 300자를 넘으면 MEMO_TOO_LONG이고 자동 저장에서도 같다")
	void rejectsInvalidScoreAndLongMemo() {
		Application application = submitApplication("김지원", backend);

		assertBusinessError(() -> docEvaluationService.save(operator.getId(),
			input(application, Opinion.PASS, backendScores(0, 3), null), true),
			CommonErrorCode.INVALID_INPUT, DocEvaluationInputReason.INVALID_SCORE);
		assertBusinessError(() -> docEvaluationService.save(operator.getId(),
			input(application, null, List.of(new DocScoreInput(commonQuestion.getId(), 6)), null), false),
			CommonErrorCode.INVALID_INPUT, DocEvaluationInputReason.INVALID_SCORE);
		assertBusinessError(() -> docEvaluationService.save(operator.getId(),
			input(application, Opinion.PASS, backendScores(3, 3), "가".repeat(301)), true),
			CommonErrorCode.INVALID_INPUT, DocEvaluationInputReason.MEMO_TOO_LONG);
		assertBusinessError(() -> docEvaluationService.save(operator.getId(),
			input(application, null, null, "가".repeat(301)), false),
			CommonErrorCode.INVALID_INPUT, DocEvaluationInputReason.MEMO_TOO_LONG);

		SaveDocEvaluationPayload maxMemo = docEvaluationService.save(operator.getId(),
			input(application, Opinion.PASS, backendScores(1, 5), "가".repeat(300)), true);
		assertThat(maxMemo.evaluation().getMemo()).hasSize(300);
	}

	@Test
	@DisplayName("다른 포지션의 문항이나 다른 공고의 문항에 점수를 보내면 QUESTION_NOT_IN_POSTING이다")
	void rejectsQuestionOutsideApplication() {
		Application application = submitApplication("김지원", backend);

		assertBusinessError(() -> docEvaluationService.save(operator.getId(), input(application, Opinion.PASS,
			List.of(new DocScoreInput(commonQuestion.getId(), 3), new DocScoreInput(backendQuestion.getId(), 3),
				new DocScoreInput(frontendQuestion.getId(), 3)), null), true),
			CommonErrorCode.INVALID_INPUT, DocEvaluationInputReason.QUESTION_NOT_IN_POSTING);
		assertBusinessError(() -> docEvaluationService.save(operator.getId(),
			input(application, null, List.of(new DocScoreInput(-1L, 3)), null), false),
			CommonErrorCode.INVALID_INPUT, DocEvaluationInputReason.QUESTION_NOT_IN_POSTING);
		// 같은 문항을 두 번 보낸 경우는 reason 없이 COMMON_400
		assertBusinessError(() -> docEvaluationService.save(operator.getId(), input(application, null,
			List.of(new DocScoreInput(commonQuestion.getId(), 3), new DocScoreInput(commonQuestion.getId(), 4)),
			null), false), CommonErrorCode.INVALID_INPUT, null);
	}

	@Test
	@DisplayName("문항별 점수를 안 쓰는 공고는 점수 없이 저장되고, 점수를 보내도 저장하지 않는다")
	void postingWithoutScores() {
		Posting noScore = postingService.saveDraft(operator.getId(), organization.getId(), null,
			basicInput("점수 없는 공고"));
		postingService.saveDraft(operator.getId(), organization.getId(), noScore.getId(), withNoPosition());
		postingService.saveDraft(operator.getId(), organization.getId(), noScore.getId(), withQuestions(false,
			List.of(new QuestionInput(null, QuestionType.TEXT, "자기소개", null, false, 500, null, 0))));
		postingService.publish(operator.getId(), noScore.getId());
		Long questionId = questionRepository.findAllByPostingOrderBySortOrder(noScore).get(0).getId();
		Application first = submitApplication("김지원", noScore, null);
		Application second = submitApplication("이지원", noScore, null);

		SaveDocEvaluationPayload withoutScores = docEvaluationService.save(operator.getId(),
			new SaveDocEvaluationInput(first.getId(), Opinion.PASS, null, null), true);
		SaveDocEvaluationPayload withScores = docEvaluationService.save(operator.getId(),
			new SaveDocEvaluationInput(second.getId(), Opinion.FAIL, List.of(new DocScoreInput(questionId, 5)), null),
			true);

		assertThat(withoutScores.evaluation().getStatus()).isEqualTo(DocEvaluationStatus.SAVED);
		assertThat(withScores.evaluation().getStatus()).isEqualTo(DocEvaluationStatus.SAVED);
		assertThat(withScores.evaluation().getScores()).isEmpty();
	}

	@Test
	@DisplayName("저장 응답은 공고 전체에서 남은 미평가 수와 제출일 최신순으로 다음 미평가 지원서를 알려 준다")
	void remainingCountAndNext() {
		Application oldest = submitApplication("가", backend);
		Application middle = submitApplication("나", frontend);
		Application newest = submitApplication("다", backend);

		SaveDocEvaluationPayload autoSaved = docEvaluationService.save(operator.getId(),
			input(newest, null, null, "작성 중"), false);
		// 자동 저장한 지원서는 아직 미평가라 남은 수에 들어가지만 다음 지원서로는 나오지 않음
		assertThat(autoSaved.remainingCount()).isEqualTo(3);
		assertThat(autoSaved.nextUnevaluatedApplicationId()).isEqualTo(middle.getId());

		SaveDocEvaluationPayload savedMiddle = docEvaluationService.save(operator.getId(),
			input(middle, Opinion.PASS, frontendScores(3, 3), null), true);
		assertThat(savedMiddle.remainingCount()).isEqualTo(2);
		assertThat(savedMiddle.nextUnevaluatedApplicationId()).isEqualTo(newest.getId());

		SaveDocEvaluationPayload savedNewest = docEvaluationService.save(operator.getId(),
			input(newest, Opinion.PASS, backendScores(3, 3), null), true);
		assertThat(savedNewest.remainingCount()).isEqualTo(1);
		assertThat(savedNewest.nextUnevaluatedApplicationId()).isEqualTo(oldest.getId());

		SaveDocEvaluationPayload savedOldest = docEvaluationService.save(operator.getId(),
			input(oldest, Opinion.FAIL, backendScores(1, 1), null), true);
		assertThat(savedOldest.remainingCount()).isZero();
		assertThat(savedOldest.nextUnevaluatedApplicationId()).isNull();
	}

	private DocEvaluationApplicationPage page(DocEvaluationTab tab, List<Long> positionIds, String keyword) {
		return docEvaluationService.findApplications(operator.getId(), posting.getId(), tab, positionIds, keyword,
			ApplicationSort.SUBMITTED_DESC, 1);
	}

	private static List<Long> ids(List<DocEvaluationApplicationRow> rows) {
		return rows.stream().map(row -> row.application().getId()).toList();
	}

	private SaveDocEvaluationInput input(Application application, Opinion opinion, List<DocScoreInput> scores,
		String memo) {
		return new SaveDocEvaluationInput(application.getId(), opinion, scores, memo);
	}

	// 백엔드 지원서에 적용되는 문항(공통, 백엔드용) 전부에 매긴 점수
	private List<DocScoreInput> backendScores(int common, int position) {
		return List.of(new DocScoreInput(commonQuestion.getId(), common),
			new DocScoreInput(backendQuestion.getId(), position));
	}

	private List<DocScoreInput> frontendScores(int common, int position) {
		return List.of(new DocScoreInput(commonQuestion.getId(), common),
			new DocScoreInput(frontendQuestion.getId(), position));
	}

	private Application submitApplication(String name, Position position) {
		return submitApplication(name, posting, position);
	}

	private Application submitApplication(String name, Posting targetPosting, Position position) {
		User applicant = saveApplicant(name);
		Application application = applicationService.saveDraft(applicant.getId(), targetPosting.getId(),
			new ApplicationInput(position != null ? position.getId() : null, List.of(), null));
		return applicationService.submit(applicant.getId(), application.getId());
	}

	// 학교 인증을 마친 지원자
	private User saveApplicant(String name) {
		User applicant = saveUser(name);
		applicant.verifySchool(school);
		return userRepository.save(applicant);
	}

	private User saveUser(String name) {
		String phone = "0106666" + String.format("%04d", phoneSequence++);
		return userRepository.save(User.create(phone, "encoded", name, false, LocalDateTime.now()));
	}

	private static void assertBusinessError(ThrowingCallable call, ErrorCode errorCode, Enum<?> reason) {
		assertThatThrownBy(call)
			.isInstanceOf(BusinessException.class)
			.satisfies(e -> {
				assertThat(((BusinessException)e).getErrorCode()).isEqualTo(errorCode);
				assertThat(((BusinessException)e).getReason()).isEqualTo(reason == null ? null : reason.name());
			});
	}

	private PostingDraftInput basicInput(String title) {
		return new PostingDraftInput(title, "본문", "2026-2", "서울", "무료", true, false, null,
			null, null, LocalDateTime.now(), LocalDateTime.now().plusDays(7), null,
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

	private PostingDraftInput withNoPosition() {
		return new PostingDraftInput(null, null, null, null, null, null, null, null,
			null, null, null, null, null,
			true, List.of(),
			null, null, null, null, null, null, null, null,
			null, null,
			null);
	}

	private PostingDraftInput withQuestions(boolean docScorePerQuestion, List<QuestionInput> questions) {
		return new PostingDraftInput(null, null, null, null, null, null, null, null,
			null, null, null, null, null,
			null, null,
			null, null, null, null, null, null, null, null,
			docScorePerQuestion, questions,
			null);
	}

}
