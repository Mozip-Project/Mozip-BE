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
import com.mozip.domain.interview.dto.InterviewTimetable;
import com.mozip.domain.interview.entity.Interview;
import com.mozip.domain.interview.entity.InterviewApplicant;
import com.mozip.domain.interview.exception.InterviewConflictReason;
import com.mozip.domain.interview.exception.InterviewErrorCode;
import com.mozip.domain.interview.exception.InterviewInputReason;
import com.mozip.domain.interview.repository.InterviewApplicantRepository;
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

	private Organization organization;
	private User operator;
	private School school;
	private Posting posting;

	@BeforeEach
	void setUp() {
		organization = organizationRepository.save(
			Organization.create("타임테이블 테스트 단체", OrganizationType.CLUB, null, OrganizationScope.CAMPUS, null, true,
				null, null));
		operator = saveUser("운영진");
		memberRepository.save(Member.create(organization, operator, null, MemberRole.REPRESENTATIVE));
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
