package com.mozip.domain.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.data.ArgumentValue;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.TestcontainersConfiguration;
import com.mozip.domain.application.dto.AnswerInput;
import com.mozip.domain.application.dto.ApplicationInput;
import com.mozip.domain.application.entity.Application;
import com.mozip.domain.application.service.ApplicationService;
import com.mozip.domain.interview.dto.InterviewInput;
import com.mozip.domain.interview.dto.InterviewTimetable;
import com.mozip.domain.interview.entity.Interview;
import com.mozip.domain.interview.entity.InterviewApplicant;
import com.mozip.domain.interview.exception.InterviewConflictReason;
import com.mozip.domain.interview.exception.InterviewErrorCode;
import com.mozip.domain.interview.exception.InterviewInputReason;
import com.mozip.domain.interview.repository.InterviewApplicantRepository;
import com.mozip.domain.interview.repository.InterviewerRepository;
import com.mozip.domain.interview.timetable.Slot;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.OrganizationScope;
import com.mozip.domain.organization.entity.OrganizationType;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.repository.OrganizationRepository;
import com.mozip.domain.posting.dto.InterviewTimeRangeInput;
import com.mozip.domain.posting.dto.PostingDraftInput;
import com.mozip.domain.posting.dto.QuestionInput;
import com.mozip.domain.posting.entity.AssignRule;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.entity.QuestionType;
import com.mozip.domain.posting.exception.PostingErrorCode;
import com.mozip.domain.posting.repository.QuestionRepository;
import com.mozip.domain.posting.service.PostingService;
import com.mozip.domain.school.entity.School;
import com.mozip.domain.school.repository.SchoolRepository;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;

import jakarta.persistence.EntityManager;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class InterviewTimetableServiceTest {

	// 10:00~11:00에 면접 20분, 쉬는 시간 10분이면 블록은 10:00, 10:30 두 개. 한 칸에 1명, 동시 진행 1개
	private static final LocalDate DATE = LocalDate.now().plusDays(10);
	private static final LocalDateTime FIRST_SLOT = DATE.atTime(10, 0);
	private static final LocalDateTime SECOND_SLOT = DATE.atTime(10, 30);

	@Autowired
	private InterviewTimetableService interviewTimetableService;

	@Autowired
	private InterviewApplicantRepository interviewApplicantRepository;

	@Autowired
	private ApplicationService applicationService;

	@Autowired
	private PostingService postingService;

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
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private InterviewerRepository interviewerRepository;

	private Organization organization;
	private User operator;
	private Member operatorMember;
	private School school;
	private Posting posting;

	@BeforeEach
	void setUp() {
		organization = organizationRepository.save(
			Organization.create("타임테이블 테스트 단체", OrganizationType.CLUB, null, OrganizationScope.CAMPUS, null, true,
				null, null));
		operator = saveUser("운영진");
		operatorMember = memberRepository.save(Member.create(organization, operator, null, MemberRole.REPRESENTATIVE));
		school = schoolRepository.save(School.create("타임테이블 테스트대학교", null));
		posting = recruitingPosting(true);
	}

	@Test
	@DisplayName("생성 전에 조회하면 면접 칸은 비어 있고 서류 합격자가 전부 미배정으로 나온다")
	void findBeforeGenerate() {
		Application passed = submit("합격자", List.of(FIRST_SLOT));
		submit("심사 전", List.of(SECOND_SLOT));
		decideDocResult(passed, "PASS");

		InterviewTimetable timetable = interviewTimetableService.find(operator.getId(), posting.getId());

		assertThat(timetable.confirmedAt()).isNull();
		assertThat(timetable.slots()).extracting(Slot::startAt).containsExactly(FIRST_SLOT, SECOND_SLOT);
		assertThat(timetable.interviews()).isEmpty();
		assertThat(timetable.unassignedApplicants()).extracting(Application::getId).containsExactly(passed.getId());
		assertThat(timetable.recentLocations()).isEmpty();
	}

	@Test
	@DisplayName("생성하면 서류 합격자만 각자 고른 시각의 칸에 배정된다")
	void generateAssignsPassedOnly() {
		Application first = submit("합격자 1", List.of(FIRST_SLOT));
		Application second = submit("합격자 2", List.of(SECOND_SLOT));
		Application failed = submit("불합격자", List.of(FIRST_SLOT));
		decideDocResult(first, "PASS");
		decideDocResult(second, "PASS");
		decideDocResult(failed, "FAIL");

		InterviewTimetable timetable = interviewTimetableService.generate(operator.getId(), posting.getId());

		assertThat(timetable.interviews()).extracting(Interview::getStartAt).containsExactly(FIRST_SLOT, SECOND_SLOT);
		assertThat(timetable.interviews()).extracting(Interview::getTrack).containsOnly(1);
		assertThat(applicationIdsOf(timetable.interviews().get(0))).containsExactly(first.getId());
		assertThat(applicationIdsOf(timetable.interviews().get(1))).containsExactly(second.getId());
		assertThat(timetable.unassignedApplicants()).isEmpty();
	}

	@Test
	@DisplayName("같은 칸에 정원보다 많이 몰리면 먼저 낸 지원자가 배정되고 나머지는 미배정으로 남는다")
	void generateLeavesOverflowUnassigned() {
		Application early = submit("먼저 낸 지원자", List.of(FIRST_SLOT));
		Application late = submit("늦게 낸 지원자", List.of(FIRST_SLOT));
		decideDocResult(early, "PASS");
		decideDocResult(late, "PASS");

		InterviewTimetable timetable = interviewTimetableService.generate(operator.getId(), posting.getId());

		assertThat(timetable.interviews()).hasSize(1);
		assertThat(applicationIdsOf(timetable.interviews().getFirst())).containsExactly(early.getId());
		assertThat(timetable.unassignedApplicants()).extracting(Application::getId).containsExactly(late.getId());
	}

	@Test
	@DisplayName("다시 생성하면 기존 배정을 지우고 새로 만든다")
	void generateTwiceReplaces() {
		Application application = submit("합격자", List.of(FIRST_SLOT));
		decideDocResult(application, "PASS");
		Long before = interviewTimetableService.generate(operator.getId(), posting.getId())
			.interviews().getFirst().getId();

		InterviewTimetable timetable = interviewTimetableService.generate(operator.getId(), posting.getId());

		assertThat(timetable.interviews()).hasSize(1);
		assertThat(timetable.interviews().getFirst().getId()).isNotEqualTo(before);
		assertThat(applicationIdsOf(timetable.interviews().getFirst())).containsExactly(application.getId());
	}

	@Test
	@DisplayName("타임테이블이 확정된 뒤에 생성하면 INTERVIEW_409(TIMETABLE_CONFIRMED)를 던진다")
	void rejectGenerateAfterConfirmed() {
		// 확정 메서드가 아직 없어서 컬럼을 직접 채움
		entityManager.flush();
		jdbcTemplate.update("update postings set timetable_confirmed_at = now() where id = ?", posting.getId());
		entityManager.clear();

		assertThatThrownBy(() -> interviewTimetableService.generate(operator.getId(), posting.getId()))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(InterviewErrorCode.CONFLICT);
				assertThat(e.getReason()).isEqualTo(InterviewConflictReason.TIMETABLE_CONFIRMED.name());
			});
	}

	@Test
	@DisplayName("면접 없는 공고에 생성하면 COMMON_400(NO_INTERVIEW)을 던진다")
	void rejectGenerateWithoutInterview() {
		Posting noInterview = recruitingPosting(false);

		assertThatThrownBy(() -> interviewTimetableService.generate(operator.getId(), noInterview.getId()))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
				assertThat(e.getReason()).isEqualTo(InterviewInputReason.NO_INTERVIEW.name());
			});
	}

	@Test
	@DisplayName("그 단체의 운영진이 아니면 조회와 생성 모두 COMMON_403을 던진다")
	void rejectOutsider() {
		User outsider = saveUser("외부인");

		assertThatThrownBy(() -> interviewTimetableService.find(outsider.getId(), posting.getId()))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
		assertThatThrownBy(() -> interviewTimetableService.generate(outsider.getId(), posting.getId()))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
	}

	@Test
	@DisplayName("공고가 없으면 조회와 생성 모두 POSTING_404를 던진다")
	void rejectMissingPosting() {
		assertThatThrownBy(() -> interviewTimetableService.find(operator.getId(), Long.MAX_VALUE))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(PostingErrorCode.NOT_FOUND));
		assertThatThrownBy(() -> interviewTimetableService.generate(operator.getId(), Long.MAX_VALUE))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(PostingErrorCode.NOT_FOUND));
	}

	@Test
	@DisplayName("새 칸을 추가하며 지원자를 넣으면 기존 칸에서 빠져 옮겨 오고 장소와 면접관이 저장된다")
	void updateMovesApplicantToNewInterview() {
		Application application = submit("합격자", List.of(FIRST_SLOT));
		decideDocResult(application, "PASS");
		Long before = interviewTimetableService.generate(operator.getId(), posting.getId())
			.interviews().getFirst().getId();

		InterviewTimetable timetable = interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(null, SECOND_SLOT, " 301호 ", List.of(application.getId()),
				List.of(operatorMember.getId())));

		assertThat(timetable.interviews()).extracting(Interview::getStartAt).containsExactly(FIRST_SLOT, SECOND_SLOT);
		Interview emptied = timetable.interviews().get(0);
		Interview added = timetable.interviews().get(1);
		assertThat(emptied.getId()).isEqualTo(before);
		assertThat(applicationIdsOf(emptied)).isEmpty();
		assertThat(applicationIdsOf(added)).containsExactly(application.getId());
		assertThat(added.getLocation()).isEqualTo("301호");
		assertThat(interviewerMemberIdsOf(added)).containsExactly(operatorMember.getId());
		assertThat(timetable.unassignedApplicants()).isEmpty();
		assertThat(timetable.recentLocations()).containsExactly("301호");
	}

	@Test
	@DisplayName("칸의 지원자 목록에서 빼면 그 지원자는 미배정으로 돌아가고 보내지 않은 장소는 그대로다")
	void updateRemovesApplicant() {
		Application application = submit("합격자", List.of(FIRST_SLOT));
		decideDocResult(application, "PASS");
		Long interviewId = interviewTimetableService.generate(operator.getId(), posting.getId())
			.interviews().getFirst().getId();
		interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(interviewId, null, "301호", null, null));

		InterviewTimetable timetable = interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(interviewId, null, null, List.of(), null));

		assertThat(applicationIdsOf(timetable.interviews().getFirst())).isEmpty();
		assertThat(timetable.interviews().getFirst().getLocation()).isEqualTo("301호");
		assertThat(timetable.unassignedApplicants()).extracting(Application::getId)
			.containsExactly(application.getId());
	}

	@Test
	@DisplayName("같은 시각에 칸을 또 추가하면 track이 다음 번호로 매겨진다")
	void addInterviewAtSameSlotGetsNextTrack() {
		interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(null, FIRST_SLOT, null, null, null));

		InterviewTimetable timetable = interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(null, FIRST_SLOT, null, null, null));

		assertThat(timetable.interviews()).extracting(Interview::getTrack).containsExactly(1, 2);
		assertThat(timetable.interviews()).extracting(Interview::getEndAt).containsOnly(FIRST_SLOT.plusMinutes(20));
	}

	@Test
	@DisplayName("블록 시작 시각이 아닌 시각에 칸을 추가하면 COMMON_400(SLOT_NOT_OPEN), 시각이 없으면 START_AT_REQUIRED를 던진다")
	void rejectAddAtNotOpenSlot() {
		assertThatThrownBy(() -> interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(null, DATE.atTime(10, 15), null, null, null)))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
				assertThat(e.getReason()).isEqualTo(InterviewInputReason.SLOT_NOT_OPEN.name());
			});
		assertThatThrownBy(() -> interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(null, null, null, null, null)))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
				assertThat(e.getReason()).isEqualTo(InterviewInputReason.START_AT_REQUIRED.name());
			});
	}

	@Test
	@DisplayName("서류 합격자가 아닌 지원서를 배정하면 COMMON_400(APPLICANT_NOT_ELIGIBLE)을 던진다")
	void rejectNotPassedApplicant() {
		Application pending = submit("심사 전", List.of(FIRST_SLOT));

		assertThatThrownBy(() -> interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(null, FIRST_SLOT, null, List.of(pending.getId()), null)))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
				assertThat(e.getReason()).isEqualTo(InterviewInputReason.APPLICANT_NOT_ELIGIBLE.name());
			});
	}

	@Test
	@DisplayName("다른 단체 운영진을 면접관으로 넣으면 COMMON_400(INTERVIEWER_NOT_MEMBER)을 던진다")
	void rejectInterviewerOfOtherOrganization() {
		Organization other = organizationRepository.save(
			Organization.create("다른 단체", OrganizationType.CLUB, null, OrganizationScope.CAMPUS, null, true,
				null, null));
		Member outsider = memberRepository.save(
			Member.create(other, saveUser("다른 단체 운영진"), null, MemberRole.REPRESENTATIVE));

		assertThatThrownBy(() -> interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(null, FIRST_SLOT, null, null, List.of(outsider.getId()))))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
				assertThat(e.getReason()).isEqualTo(InterviewInputReason.INTERVIEWER_NOT_MEMBER.name());
			});
	}

	@Test
	@DisplayName("같은 시각의 다른 칸에 이미 들어간 면접관을 또 넣으면 INTERVIEW_409(INTERVIEWER_TIME_OVERLAP)를 던진다")
	void rejectOverlappingInterviewer() {
		interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(null, FIRST_SLOT, null, null, List.of(operatorMember.getId())));

		assertThatThrownBy(() -> interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(null, FIRST_SLOT, null, null, List.of(operatorMember.getId()))))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(InterviewErrorCode.CONFLICT);
				assertThat(e.getReason()).isEqualTo(InterviewConflictReason.INTERVIEWER_TIME_OVERLAP.name());
			});
	}

	@Test
	@DisplayName("같은 칸의 면접관을 같은 사람으로 다시 보내거나 시간이 안 겹치는 칸에 넣는 것은 된다")
	void allowSameInterviewerWithoutOverlap() {
		Long interviewId = interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(null, FIRST_SLOT, null, null, List.of(operatorMember.getId())))
			.interviews().getFirst().getId();

		interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(interviewId, null, null, null, List.of(operatorMember.getId())));
		InterviewTimetable timetable = interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(null, SECOND_SLOT, null, null, List.of(operatorMember.getId())));

		assertThat(timetable.interviews()).hasSize(2)
			.allSatisfy(interview -> assertThat(interviewerMemberIdsOf(interview))
				.containsExactly(operatorMember.getId()));
	}

	@Test
	@DisplayName("다른 공고의 칸을 고치려 하면 INTERVIEW_404를 던진다")
	void rejectInterviewOfOtherPosting() {
		Posting other = recruitingPosting(true);
		Long otherInterviewId = interviewTimetableService.update(operator.getId(), other.getId(),
			new InterviewInput(null, FIRST_SLOT, null, null, null)).interviews().getFirst().getId();

		assertThatThrownBy(() -> interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(otherInterviewId, null, "301호", null, null)))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(InterviewErrorCode.NOT_FOUND));
	}

	@Test
	@DisplayName("칸을 지우면 거기 있던 지원자는 미배정으로 돌아간다")
	void deleteInterview() {
		Application application = submit("합격자", List.of(FIRST_SLOT));
		decideDocResult(application, "PASS");
		Long interviewId = interviewTimetableService.generate(operator.getId(), posting.getId())
			.interviews().getFirst().getId();
		interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(interviewId, null, null, null, List.of(operatorMember.getId())));

		InterviewTimetable timetable = interviewTimetableService.delete(operator.getId(), interviewId);

		assertThat(timetable.interviews()).isEmpty();
		assertThat(timetable.unassignedApplicants()).extracting(Application::getId)
			.containsExactly(application.getId());
	}

	@Test
	@DisplayName("확정하면 확정 시각이 채워지고 배정된 지원자에게만 알림이 한 건씩 간다")
	void confirmNotifiesAssignedApplicants() {
		Application assigned = submit("배정된 지원자", List.of(FIRST_SLOT));
		Application unassigned = submit("미배정 지원자", List.of(FIRST_SLOT));
		decideDocResult(assigned, "PASS");
		decideDocResult(unassigned, "PASS");
		interviewTimetableService.generate(operator.getId(), posting.getId());

		InterviewTimetable timetable = interviewTimetableService.confirm(operator.getId(), posting.getId());

		assertThat(timetable.confirmedAt()).isNotNull();
		assertThat(timetable.unassignedApplicants()).extracting(Application::getId)
			.containsExactly(unassigned.getId());
		entityManager.flush();
		assertThat(noticeCount(assigned)).isEqualTo(1);
		assertThat(noticeCount(unassigned)).isZero();
	}

	@Test
	@DisplayName("확정한 뒤에는 다시 확정과 자동 생성이 INTERVIEW_409로 막히고 칸 수정은 된다")
	void afterConfirm() {
		Application application = submit("합격자", List.of(FIRST_SLOT));
		decideDocResult(application, "PASS");
		Long interviewId = interviewTimetableService.generate(operator.getId(), posting.getId())
			.interviews().getFirst().getId();
		interviewTimetableService.confirm(operator.getId(), posting.getId());

		assertThatThrownBy(() -> interviewTimetableService.confirm(operator.getId(), posting.getId()))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(InterviewErrorCode.CONFLICT);
				assertThat(e.getReason()).isEqualTo(InterviewConflictReason.ALREADY_CONFIRMED.name());
			});
		assertThatThrownBy(() -> interviewTimetableService.generate(operator.getId(), posting.getId()))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(InterviewErrorCode.CONFLICT);
				assertThat(e.getReason()).isEqualTo(InterviewConflictReason.TIMETABLE_CONFIRMED.name());
			});
		InterviewTimetable timetable = interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(interviewId, null, "301호", null, null));
		assertThat(timetable.confirmedAt()).isNotNull();
		assertThat(timetable.interviews().getFirst().getLocation()).isEqualTo("301호");
	}

	@Test
	@DisplayName("배정된 지원자가 한 명도 없으면 확정이 INTERVIEW_409(NO_ASSIGNED_APPLICANT)로 막힌다")
	void rejectConfirmWithoutAssignedApplicant() {
		assertThatThrownBy(() -> interviewTimetableService.confirm(operator.getId(), posting.getId()))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(InterviewErrorCode.CONFLICT);
				assertThat(e.getReason()).isEqualTo(InterviewConflictReason.NO_ASSIGNED_APPLICANT.name());
			});
	}

	private int noticeCount(Application application) {
		return jdbcTemplate.queryForObject(
			"select count(*) from notifications where user_id = ? and type = 'POSTING_NOTICE'", Integer.class,
			application.getUser().getId());
	}

	private List<Long> interviewerMemberIdsOf(Interview interview) {
		return interviewerRepository.findAllWithMemberByInterviewIn(List.of(interview)).stream()
			.map(interviewer -> interviewer.getMember().getId())
			.toList();
	}

	private List<Long> applicationIdsOf(Interview interview) {
		return interviewApplicantRepository.findAllWithApplicationByInterviewIn(List.of(interview)).stream()
			.map(InterviewApplicant::getApplication)
			.map(Application::getId)
			.toList();
	}

	private Application submit(String name, List<LocalDateTime> availableTimes) {
		User applicant = saveUser(name);
		applicant.verifySchool(school);
		userRepository.save(applicant);
		Long questionId = questionRepository.findAllByPostingOrderBySortOrder(posting).get(0).getId();
		Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(null, List.of(new AnswerInput(questionId, "답변", null)), availableTimes));
		return applicationService.submit(applicant.getId(), application.getId());
	}

	// 서류 결과 확정은 서류 평가 이슈에서 생겨서 컬럼을 직접 채움
	private void decideDocResult(Application application, String docResult) {
		entityManager.flush();
		jdbcTemplate.update("update applications set doc_result = ? where id = ?", docResult, application.getId());
		entityManager.clear();
	}

	private Posting recruitingPosting(boolean hasInterview) {
		Posting created = postingService.saveDraft(operator.getId(), organization.getId(), null,
			new PostingDraftInput("타임테이블 공고 " + System.nanoTime(), "본문", ArgumentValue.ofNullable("2026-2"),
				ArgumentValue.ofNullable("서울"), ArgumentValue.ofNullable("무료"), true, false, null,
				null, null, LocalDateTime.now(), LocalDateTime.now().plusDays(7), null,
				true, null,
				null, null, null, null, null, null, null,
				null, null));
		if (hasInterview) {
			postingService.saveDraft(operator.getId(), organization.getId(), created.getId(),
				new PostingDraftInput(null, null, null, null, null, null, null, null,
					null, null, null, null, null,
					null, null,
					true, 20, 10, AssignRule.ALL_MEMBERS, 1, 1,
					List.of(new InterviewTimeRangeInput(null, DATE, LocalTime.of(10, 0), LocalTime.of(11, 0), List.of())),
					null, null));
		}
		postingService.saveDraft(operator.getId(), organization.getId(), created.getId(),
			new PostingDraftInput(null, null, null, null, null, null, null, null,
				null, null, null, null, null,
				null, null,
				null, null, null, null, null, null, null,
				true, List.of(new QuestionInput(null, QuestionType.TEXT, "자기소개", null, true, 500, null, 0))));
		postingService.publish(operator.getId(), created.getId());
		return created;
	}

	private User saveUser(String name) {
		String phone = "0106" + String.format("%07d", ThreadLocalRandom.current().nextInt(10_000_000));
		return userRepository.save(User.create(phone, "encoded", name, false, LocalDateTime.now()));
	}

}
