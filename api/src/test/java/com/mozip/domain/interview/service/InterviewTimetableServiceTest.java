package com.mozip.domain.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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
import com.mozip.domain.interview.dto.InterviewerCandidate;
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
	@DisplayName("빈 칸으로 지원자를 옮기면 새 칸이 생기고 비게 된 원래 칸은 지워진다")
	void updateMovesApplicantToEmptyCell() {
		Application application = submit("합격자", List.of(FIRST_SLOT));
		decideDocResult(application, "PASS");
		interviewTimetableService.generate(operator.getId(), posting.getId());

		InterviewTimetable timetable = interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(null, SECOND_SLOT, null, " 301호 ", List.of(application.getId()),
				List.of(operatorMember.getId())));

		assertThat(timetable.interviews()).extracting(Interview::getStartAt).containsExactly(SECOND_SLOT);
		Interview moved = timetable.interviews().getFirst();
		assertThat(moved.getTrack()).isEqualTo(1);
		assertThat(moved.getEndAt()).isEqualTo(SECOND_SLOT.plusMinutes(20));
		assertThat(applicationIdsOf(moved)).containsExactly(application.getId());
		assertThat(moved.getLocation()).isEqualTo("301호");
		assertThat(interviewerMemberIdsOf(moved)).containsExactly(operatorMember.getId());
		assertThat(timetable.unassignedApplicants()).isEmpty();
		assertThat(timetable.recentLocations()).containsExactly("301호");
		assertThat(timetable.trackCount()).isEqualTo(1);
		assertThat(timetable.editing()).isFalse();
	}

	@Test
	@DisplayName("칸의 지원자를 모두 빼면 지원자는 미배정으로 돌아가고 칸은 면접관과 함께 지워진다")
	void updateRemovesApplicant() {
		Application application = submit("합격자", List.of(FIRST_SLOT));
		decideDocResult(application, "PASS");
		Long interviewId = interviewTimetableService.generate(operator.getId(), posting.getId())
			.interviews().getFirst().getId();
		interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(interviewId, null, null, "301호", null, List.of(operatorMember.getId())));

		InterviewTimetable timetable = interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(interviewId, null, null, null, List.of(), null));

		assertThat(timetable.interviews()).isEmpty();
		assertThat(timetable.unassignedApplicants()).extracting(Application::getId)
			.containsExactly(application.getId());
		assertThat(interviewerRepository.count()).isZero();
	}

	@Test
	@DisplayName("이미 배정된 칸에 지원자를 더 넣으면 정원과 상관없이 그룹 면접이 된다")
	void updateMakesGroupInterview() {
		Application early = submit("먼저 낸 지원자", List.of(FIRST_SLOT));
		Application late = submit("늦게 낸 지원자", List.of(FIRST_SLOT));
		decideDocResult(early, "PASS");
		decideDocResult(late, "PASS");
		interviewTimetableService.generate(operator.getId(), posting.getId());

		// 칸을 id 대신 시각과 열 번호로 가리킴
		InterviewTimetable timetable = interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(null, FIRST_SLOT, 1, null, List.of(early.getId(), late.getId()), null));

		assertThat(timetable.interviews()).hasSize(1);
		assertThat(applicationIdsOf(timetable.interviews().getFirst()))
			.containsExactly(early.getId(), late.getId());
		assertThat(timetable.unassignedApplicants()).isEmpty();
	}

	@Test
	@DisplayName("면접실 열을 늘리면 같은 시각의 새 열에 배정할 수 있고 열 개수를 넘는 번호는 COMMON_400(TRACK_OUT_OF_RANGE)이다")
	void addTrack() {
		Application early = submit("먼저 낸 지원자", List.of(FIRST_SLOT));
		Application late = submit("늦게 낸 지원자", List.of(FIRST_SLOT));
		decideDocResult(early, "PASS");
		decideDocResult(late, "PASS");
		interviewTimetableService.generate(operator.getId(), posting.getId());
		// 열이 하나뿐이고 그 시각의 칸이 차 있어서 열 번호 없이는 놓을 곳이 없음
		assertThatThrownBy(() -> interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(null, FIRST_SLOT, null, null, List.of(late.getId()), null)))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getReason()).isEqualTo(InterviewInputReason.TRACK_OUT_OF_RANGE.name()));

		assertThat(interviewTimetableService.addTrack(operator.getId(), posting.getId()).trackCount()).isEqualTo(2);
		InterviewTimetable timetable = interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(null, FIRST_SLOT, null, null, List.of(late.getId()), null));

		assertThat(timetable.interviews()).extracting(Interview::getTrack).containsExactly(1, 2);
		assertThat(applicationIdsOf(timetable.interviews().get(1))).containsExactly(late.getId());
		assertThatThrownBy(() -> interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(null, SECOND_SLOT, 3, null, List.of(late.getId()), null)))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
				assertThat(e.getReason()).isEqualTo(InterviewInputReason.TRACK_OUT_OF_RANGE.name());
			});
	}

	@Test
	@DisplayName("늘린 면접실 열은 다시 생성할 때도 남아서 같은 시각에 그만큼 더 배정된다")
	void generateUsesAddedTrack() {
		Application early = submit("먼저 낸 지원자", List.of(FIRST_SLOT));
		Application late = submit("늦게 낸 지원자", List.of(FIRST_SLOT));
		decideDocResult(early, "PASS");
		decideDocResult(late, "PASS");
		interviewTimetableService.addTrack(operator.getId(), posting.getId());

		InterviewTimetable timetable = interviewTimetableService.generate(operator.getId(), posting.getId());

		assertThat(timetable.trackCount()).isEqualTo(2);
		assertThat(timetable.interviews()).extracting(Interview::getStartAt).containsOnly(FIRST_SLOT);
		assertThat(timetable.interviews()).extracting(Interview::getTrack).containsExactly(1, 2);
		assertThat(timetable.unassignedApplicants()).isEmpty();
	}

	@Test
	@DisplayName("id 없이 칸을 가리킬 때 블록 시작 시각이 아니면 SLOT_NOT_OPEN, 시각이 없으면 START_AT_REQUIRED, 빈 칸인데 지원자가 없으면 APPLICANTS_REQUIRED다")
	void rejectInvalidCell() {
		assertThatThrownBy(() -> interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(null, DATE.atTime(10, 15), null, null, null, null)))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
				assertThat(e.getReason()).isEqualTo(InterviewInputReason.SLOT_NOT_OPEN.name());
			});
		assertThatThrownBy(() -> interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(null, null, null, null, null, null)))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
				assertThat(e.getReason()).isEqualTo(InterviewInputReason.START_AT_REQUIRED.name());
			});
		assertThatThrownBy(() -> interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(null, FIRST_SLOT, null, "301호", List.of(), null)))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
				assertThat(e.getReason()).isEqualTo(InterviewInputReason.APPLICANTS_REQUIRED.name());
			});
	}

	@Test
	@DisplayName("서류 합격자가 아닌 지원서를 배정하면 COMMON_400(APPLICANT_NOT_ELIGIBLE)을 던진다")
	void rejectNotPassedApplicant() {
		Application pending = submit("심사 전", List.of(FIRST_SLOT));

		assertThatThrownBy(() -> interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(null, FIRST_SLOT, null, null, List.of(pending.getId()), null)))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
				assertThat(e.getReason()).isEqualTo(InterviewInputReason.APPLICANT_NOT_ELIGIBLE.name());
			});
	}

	@Test
	@DisplayName("다른 단체 운영진을 면접관으로 넣으면 COMMON_400(INTERVIEWER_NOT_MEMBER)을 던진다")
	void rejectInterviewerOfOtherOrganization() {
		Application application = submit("합격자", List.of(FIRST_SLOT));
		decideDocResult(application, "PASS");
		Long interviewId = interviewTimetableService.generate(operator.getId(), posting.getId())
			.interviews().getFirst().getId();
		Organization other = organizationRepository.save(
			Organization.create("다른 단체", OrganizationType.CLUB, null, OrganizationScope.CAMPUS, null, true,
				null, null));
		Member outsider = memberRepository.save(
			Member.create(other, saveUser("다른 단체 운영진"), null, MemberRole.REPRESENTATIVE));

		assertThatThrownBy(() -> interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(interviewId, null, null, null, null, List.of(outsider.getId()))))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
				assertThat(e.getReason()).isEqualTo(InterviewInputReason.INTERVIEWER_NOT_MEMBER.name());
			});
	}

	@Test
	@DisplayName("같은 시각의 다른 칸에 이미 들어간 면접관을 또 넣으면 INTERVIEW_409(INTERVIEWER_TIME_OVERLAP)를 던진다")
	void rejectOverlappingInterviewer() {
		Application early = submit("먼저 낸 지원자", List.of(FIRST_SLOT));
		Application late = submit("늦게 낸 지원자", List.of(FIRST_SLOT));
		decideDocResult(early, "PASS");
		decideDocResult(late, "PASS");
		Long firstId = interviewTimetableService.generate(operator.getId(), posting.getId())
			.interviews().getFirst().getId();
		interviewTimetableService.addTrack(operator.getId(), posting.getId());
		interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(null, FIRST_SLOT, 2, null, List.of(late.getId()), List.of(operatorMember.getId())));

		assertThatThrownBy(() -> interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(firstId, null, null, null, null, List.of(operatorMember.getId()))))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(InterviewErrorCode.CONFLICT);
				assertThat(e.getReason()).isEqualTo(InterviewConflictReason.INTERVIEWER_TIME_OVERLAP.name());
			});
	}

	@Test
	@DisplayName("같은 칸의 면접관을 같은 사람으로 다시 보내거나 시간이 안 겹치는 칸에 넣는 것은 된다")
	void allowSameInterviewerWithoutOverlap() {
		Application first = submit("합격자 1", List.of(FIRST_SLOT));
		Application second = submit("합격자 2", List.of(SECOND_SLOT));
		decideDocResult(first, "PASS");
		decideDocResult(second, "PASS");
		List<Interview> generated = interviewTimetableService.generate(operator.getId(), posting.getId()).interviews();
		Long firstId = generated.get(0).getId();
		Long secondId = generated.get(1).getId();
		interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(firstId, null, null, null, null, List.of(operatorMember.getId())));

		interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(firstId, null, null, null, null, List.of(operatorMember.getId())));
		InterviewTimetable timetable = interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(secondId, null, null, null, null, List.of(operatorMember.getId())));

		assertThat(timetable.interviews()).hasSize(2)
			.allSatisfy(interview -> assertThat(interviewerMemberIdsOf(interview))
				.containsExactly(operatorMember.getId()));
	}

	@Test
	@DisplayName("면접관 후보는 같은 시각의 다른 칸에 들어가 있으면 그 면접이 같이 나오고, 지금 고르는 칸이거나 다른 시각이면 비어 있다")
	void interviewerCandidates() {
		Application application = submit("합격자", List.of(FIRST_SLOT));
		decideDocResult(application, "PASS");
		Long interviewId = interviewTimetableService.generate(operator.getId(), posting.getId())
			.interviews().getFirst().getId();
		interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(interviewId, null, null, null, null, List.of(operatorMember.getId())));

		List<InterviewerCandidate> sameTime = interviewTimetableService.interviewerCandidates(operator.getId(),
			posting.getId(), FIRST_SLOT, null);
		List<InterviewerCandidate> sameCell = interviewTimetableService.interviewerCandidates(operator.getId(),
			posting.getId(), FIRST_SLOT, interviewId);
		List<InterviewerCandidate> otherTime = interviewTimetableService.interviewerCandidates(operator.getId(),
			posting.getId(), SECOND_SLOT, null);

		assertThat(sameTime).hasSize(1);
		assertThat(sameTime.getFirst().member().getId()).isEqualTo(operatorMember.getId());
		assertThat(sameTime.getFirst().busyInterview().getId()).isEqualTo(interviewId);
		assertThat(sameCell.getFirst().busyInterview()).isNull();
		assertThat(otherTime.getFirst().busyInterview()).isNull();
	}

	@Test
	@DisplayName("다른 공고의 칸을 고치려 하면 INTERVIEW_404를 던진다")
	void rejectInterviewOfOtherPosting() {
		Application application = submit("합격자", List.of(FIRST_SLOT));
		decideDocResult(application, "PASS");
		Long interviewId = interviewTimetableService.generate(operator.getId(), posting.getId())
			.interviews().getFirst().getId();
		Posting other = recruitingPosting(true);

		assertThatThrownBy(() -> interviewTimetableService.update(operator.getId(), other.getId(),
			new InterviewInput(interviewId, null, null, "301호", null, null)))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(InterviewErrorCode.NOT_FOUND));
	}

	@Test
	@DisplayName("장소를 한 번에 입력하면 그 날짜, 그 열의 모든 칸 장소가 바뀌고 다른 날짜는 그대로다")
	void updateLocations() {
		Application first = submit("합격자 1", List.of(FIRST_SLOT));
		Application second = submit("합격자 2", List.of(SECOND_SLOT));
		decideDocResult(first, "PASS");
		decideDocResult(second, "PASS");
		Long firstId = interviewTimetableService.generate(operator.getId(), posting.getId())
			.interviews().getFirst().getId();
		interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(firstId, null, null, "따로 정한 곳", null, null));

		InterviewTimetable other = interviewTimetableService.updateLocations(operator.getId(), posting.getId(),
			DATE.plusDays(1), 1, "다른 날");
		assertThat(other.interviews()).extracting(Interview::getLocation).containsExactly("따로 정한 곳", null);

		InterviewTimetable timetable = interviewTimetableService.updateLocations(operator.getId(), posting.getId(),
			DATE, 1, "301호");

		assertThat(timetable.interviews()).extracting(Interview::getLocation).containsExactly("301호", "301호");
	}

	@Test
	@DisplayName("미배정 지원자가 남아 있으면 확정이 INTERVIEW_409(UNASSIGNED_APPLICANT_REMAINS)로 막히고, 모두 배정하면 알림 없이 확정된다")
	void confirmRequiresAllAssigned() {
		Application early = submit("먼저 낸 지원자", List.of(FIRST_SLOT));
		Application late = submit("늦게 낸 지원자", List.of(FIRST_SLOT));
		decideDocResult(early, "PASS");
		decideDocResult(late, "PASS");
		Long interviewId = interviewTimetableService.generate(operator.getId(), posting.getId())
			.interviews().getFirst().getId();

		assertThatThrownBy(() -> interviewTimetableService.confirm(operator.getId(), posting.getId()))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(InterviewErrorCode.CONFLICT);
				assertThat(e.getReason()).isEqualTo(InterviewConflictReason.UNASSIGNED_APPLICANT_REMAINS.name());
			});

		interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(interviewId, null, null, null, List.of(early.getId(), late.getId()), null));
		InterviewTimetable timetable = interviewTimetableService.confirm(operator.getId(), posting.getId());

		assertThat(timetable.confirmedAt()).isNotNull();
		assertThat(timetable.editing()).isFalse();
		entityManager.flush();
		assertThat(noticeCount(early)).isZero();
		assertThat(noticeCount(late)).isZero();
	}

	@Test
	@DisplayName("확정한 뒤 보기 모드에서는 장소만 바꿀 수 있고 다시 확정, 자동 생성, 배정 변경, 열 추가, 수정 취소는 INTERVIEW_409다")
	void afterConfirm() {
		Application application = submit("합격자", List.of(FIRST_SLOT));
		decideDocResult(application, "PASS");
		Long interviewId = interviewTimetableService.generate(operator.getId(), posting.getId())
			.interviews().getFirst().getId();
		interviewTimetableService.confirm(operator.getId(), posting.getId());

		assertConflict(() -> interviewTimetableService.confirm(operator.getId(), posting.getId()),
			InterviewConflictReason.ALREADY_CONFIRMED);
		assertConflict(() -> interviewTimetableService.generate(operator.getId(), posting.getId()),
			InterviewConflictReason.TIMETABLE_CONFIRMED);
		assertConflict(() -> interviewTimetableService.update(operator.getId(), posting.getId(),
				new InterviewInput(interviewId, null, null, null, List.of(), null)),
			InterviewConflictReason.EDIT_MODE_REQUIRED);
		assertConflict(() -> interviewTimetableService.update(operator.getId(), posting.getId(),
				new InterviewInput(interviewId, null, null, null, null, List.of(operatorMember.getId()))),
			InterviewConflictReason.EDIT_MODE_REQUIRED);
		assertConflict(() -> interviewTimetableService.addTrack(operator.getId(), posting.getId()),
			InterviewConflictReason.EDIT_MODE_REQUIRED);
		assertConflict(() -> interviewTimetableService.cancelEdit(operator.getId(), posting.getId()),
			InterviewConflictReason.NOT_EDITING);

		InterviewTimetable timetable = interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(interviewId, null, null, "301호", null, null));
		assertThat(timetable.confirmedAt()).isNotNull();
		assertThat(timetable.interviews().getFirst().getLocation()).isEqualTo("301호");
	}

	@Test
	@DisplayName("확정 전에는 수정 모드에 들어갈 수 없어 INTERVIEW_409(NOT_CONFIRMED)를 던진다")
	void rejectStartEditBeforeConfirm() {
		assertConflict(() -> interviewTimetableService.startEdit(operator.getId(), posting.getId()),
			InterviewConflictReason.NOT_CONFIRMED);
	}

	@Test
	@DisplayName("수정 모드에서 바꾼 배정, 장소, 면접관, 면접실 열은 수정을 취소하면 확정했던 상태로 돌아간다")
	void cancelEditRestoresConfirmedTimetable() {
		Application application = submit("합격자", List.of(FIRST_SLOT));
		decideDocResult(application, "PASS");
		Long interviewId = interviewTimetableService.generate(operator.getId(), posting.getId())
			.interviews().getFirst().getId();
		interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(interviewId, null, null, "301호", null, List.of(operatorMember.getId())));
		interviewTimetableService.confirm(operator.getId(), posting.getId());

		assertThat(interviewTimetableService.startEdit(operator.getId(), posting.getId()).editing()).isTrue();
		// 한 번 더 눌러도 그대로 수정 모드
		assertThat(interviewTimetableService.startEdit(operator.getId(), posting.getId()).editing()).isTrue();
		interviewTimetableService.addTrack(operator.getId(), posting.getId());
		InterviewTimetable edited = interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(null, SECOND_SLOT, 2, "B동", List.of(application.getId()), List.of()));
		assertThat(edited.editing()).isTrue();
		assertThat(edited.trackCount()).isEqualTo(2);
		assertThat(edited.interviews()).extracting(Interview::getStartAt).containsExactly(SECOND_SLOT);
		assertThat(interviewTimetableService.find(operator.getId(), posting.getId()).editing()).isTrue();

		InterviewTimetable restored = interviewTimetableService.cancelEdit(operator.getId(), posting.getId());

		assertThat(restored.editing()).isFalse();
		assertThat(restored.confirmedAt()).isNotNull();
		assertThat(restored.trackCount()).isEqualTo(1);
		assertThat(restored.interviews()).hasSize(1);
		Interview interview = restored.interviews().getFirst();
		assertThat(interview.getStartAt()).isEqualTo(FIRST_SLOT);
		assertThat(interview.getTrack()).isEqualTo(1);
		assertThat(interview.getLocation()).isEqualTo("301호");
		assertThat(applicationIdsOf(interview)).containsExactly(application.getId());
		assertThat(interviewerMemberIdsOf(interview)).containsExactly(operatorMember.getId());
		assertThat(restored.recentLocations()).containsExactly("301호");
	}

	@Test
	@DisplayName("수정 모드에서 다시 확정하면 수정한 내용이 남고 수정 모드가 끝나며, 미배정이 남아 있으면 다시 확정할 수 없다")
	void reconfirmKeepsEdits() {
		Application application = submit("합격자", List.of(FIRST_SLOT));
		decideDocResult(application, "PASS");
		Long interviewId = interviewTimetableService.generate(operator.getId(), posting.getId())
			.interviews().getFirst().getId();
		interviewTimetableService.confirm(operator.getId(), posting.getId());
		interviewTimetableService.startEdit(operator.getId(), posting.getId());

		interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(interviewId, null, null, null, List.of(), null));
		assertConflict(() -> interviewTimetableService.confirm(operator.getId(), posting.getId()),
			InterviewConflictReason.UNASSIGNED_APPLICANT_REMAINS);

		interviewTimetableService.update(operator.getId(), posting.getId(),
			new InterviewInput(null, SECOND_SLOT, null, null, List.of(application.getId()), null));
		InterviewTimetable timetable = interviewTimetableService.confirm(operator.getId(), posting.getId());

		assertThat(timetable.editing()).isFalse();
		assertThat(timetable.confirmedAt()).isNotNull();
		assertThat(timetable.interviews()).extracting(Interview::getStartAt).containsExactly(SECOND_SLOT);
		assertConflict(() -> interviewTimetableService.cancelEdit(operator.getId(), posting.getId()),
			InterviewConflictReason.NOT_EDITING);
	}

	@Test
	@DisplayName("배정된 지원자가 한 명도 없으면 확정이 INTERVIEW_409(NO_ASSIGNED_APPLICANT)로 막힌다")
	void rejectConfirmWithoutAssignedApplicant() {
		assertConflict(() -> interviewTimetableService.confirm(operator.getId(), posting.getId()),
			InterviewConflictReason.NO_ASSIGNED_APPLICANT);
	}

	private void assertConflict(ThrowingCallable call, InterviewConflictReason reason) {
		assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class, e -> {
			assertThat(e.getErrorCode()).isEqualTo(InterviewErrorCode.CONFLICT);
			assertThat(e.getReason()).isEqualTo(reason.name());
		});
	}

	private int noticeCount(Application application) {
		return jdbcTemplate.queryForObject("select count(*) from notifications where user_id = ?", Integer.class,
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
