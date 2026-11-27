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
import com.mozip.domain.application.entity.ApplicationStatus;
import com.mozip.domain.application.exception.ApplicationErrorCode;
import com.mozip.domain.application.exception.ApplicationInputReason;
import com.mozip.domain.application.service.ApplicationService;
import com.mozip.domain.interview.exception.InterviewConflictReason;
import com.mozip.domain.interview.exception.InterviewErrorCode;
import com.mozip.domain.interview.exception.InterviewInputReason;
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
class InterviewAvailabilityServiceImplTest {

	// 10:00~11:00에 면접 20분, 쉬는 시간 10분이면 블록은 10:00, 10:30 두 개
	private static final LocalDate DATE = LocalDate.now().plusDays(10);
	private static final LocalDateTime FIRST_SLOT = DATE.atTime(10, 0);
	private static final LocalDateTime SECOND_SLOT = DATE.atTime(10, 30);

	@Autowired
	private InterviewAvailabilityService interviewAvailabilityService;

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
	private User applicant;

	@BeforeEach
	void setUp() {
		organization = organizationRepository.save(
			Organization.create("면접 시간 테스트 단체", OrganizationType.CLUB, null, OrganizationScope.CAMPUS, null, true,
				null, null));
		operator = saveUser("운영진");
		memberRepository.save(Member.create(organization, operator, null, MemberRole.REPRESENTATIVE));
		applicant = saveUser("지원자");
		applicant.verifySchool(schoolRepository.save(School.create("면접 시간 테스트대학교", null)));
		userRepository.save(applicant);
	}

	@Test
	@DisplayName("제출한 지원서도 면접 가능 시간을 다시 고르면 기존 값이 통째로 바뀐다")
	void reselectAfterSubmit() {
		Application application = submittedApplication(List.of(FIRST_SLOT));

		Application reselected = interviewAvailabilityService.reselect(applicant.getId(), application.getId(),
			List.of(SECOND_SLOT));

		assertThat(reselected.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
		assertThat(reselected.getAvailableTimes()).containsExactly(SECOND_SLOT);
	}

	@Test
	@DisplayName("같은 시각을 두 번 보내면 한 번만 저장하고 시간순으로 맞춘다")
	void reselectRemovesDuplicates() {
		Application application = submittedApplication(List.of(FIRST_SLOT));

		Application reselected = interviewAvailabilityService.reselect(applicant.getId(), application.getId(),
			List.of(SECOND_SLOT, FIRST_SLOT, SECOND_SLOT));

		assertThat(reselected.getAvailableTimes()).containsExactly(FIRST_SLOT, SECOND_SLOT);
	}

	@Test
	@DisplayName("본인 지원서가 아니면 COMMON_403을 던진다")
	void rejectOthersApplication() {
		Application application = submittedApplication(List.of(FIRST_SLOT));
		User other = saveUser("다른 지원자");

		assertThatThrownBy(() -> interviewAvailabilityService.reselect(other.getId(), application.getId(),
			List.of(SECOND_SLOT)))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
	}

	@Test
	@DisplayName("지원서가 없으면 APPLICATION_404를 던진다")
	void rejectMissingApplication() {
		assertThatThrownBy(() -> interviewAvailabilityService.reselect(applicant.getId(), Long.MAX_VALUE,
			List.of(FIRST_SLOT)))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ApplicationErrorCode.NOT_FOUND));
	}

	@Test
	@DisplayName("면접 없는 공고의 지원서면 COMMON_400(NO_INTERVIEW)을 던진다")
	void rejectPostingWithoutInterview() {
		Posting posting = recruitingPosting(false);
		Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(null, List.of(), null));

		assertThatThrownBy(() -> interviewAvailabilityService.reselect(applicant.getId(), application.getId(),
			List.of(FIRST_SLOT)))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
				assertThat(e.getReason()).isEqualTo(InterviewInputReason.NO_INTERVIEW.name());
			});
	}

	@Test
	@DisplayName("빈 목록으로 다시 고르면 COMMON_400(AVAILABLE_TIMES_REQUIRED)을 던지고 기존 값은 그대로다")
	void rejectEmptyTimes() {
		Application application = submittedApplication(List.of(FIRST_SLOT));

		assertThatThrownBy(() -> interviewAvailabilityService.reselect(applicant.getId(), application.getId(),
			List.of()))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
				assertThat(e.getReason()).isEqualTo(InterviewInputReason.AVAILABLE_TIMES_REQUIRED.name());
			});
		assertThat(application.getAvailableTimes()).containsExactly(FIRST_SLOT);
	}

	@Test
	@DisplayName("열려 있는 블록의 시작 시각이 아니면 COMMON_400(AVAILABLE_TIME_NOT_OPEN)을 던진다")
	void rejectTimeNotOpen() {
		Application application = submittedApplication(List.of(FIRST_SLOT));

		assertThatThrownBy(() -> interviewAvailabilityService.reselect(applicant.getId(), application.getId(),
			List.of(DATE.atTime(10, 15))))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
				assertThat(e.getReason()).isEqualTo(ApplicationInputReason.AVAILABLE_TIME_NOT_OPEN.name());
			});
	}

	@Test
	@DisplayName("타임테이블이 확정된 뒤에는 INTERVIEW_409(TIMETABLE_CONFIRMED)를 던진다")
	void rejectAfterTimetableConfirmed() {
		Application application = submittedApplication(List.of(FIRST_SLOT));
		// 확정 메서드는 타임테이블 이슈에서 생겨서 컬럼을 직접 채움
		entityManager.flush();
		jdbcTemplate.update("update postings set timetable_confirmed_at = now() where id = ?",
			application.getPosting().getId());
		entityManager.clear();

		assertThatThrownBy(() -> interviewAvailabilityService.reselect(applicant.getId(), application.getId(),
			List.of(SECOND_SLOT)))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(InterviewErrorCode.CONFLICT);
				assertThat(e.getReason()).isEqualTo(InterviewConflictReason.TIMETABLE_CONFIRMED.name());
			});
	}

	private Application submittedApplication(List<LocalDateTime> availableTimes) {
		Posting posting = recruitingPosting(true);
		Long questionId = questionRepository.findAllByPostingOrderBySortOrder(posting).get(0).getId();
		Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(null, List.of(new AnswerInput(questionId, "답변", null)), availableTimes));
		return applicationService.submit(applicant.getId(), application.getId());
	}

	private Posting recruitingPosting(boolean hasInterview) {
		Posting posting = postingService.saveDraft(operator.getId(), organization.getId(), null,
			new PostingDraftInput("면접 시간 공고 " + System.nanoTime(), "본문", ArgumentValue.ofNullable("2026-2"),
				ArgumentValue.ofNullable("서울"), ArgumentValue.ofNullable("무료"), true, false, null,
				null, null, LocalDateTime.now(), LocalDateTime.now().plusDays(7), null,
				true, null,
				null, null, null, null, null, null, null,
				null, null));
		if (hasInterview) {
			postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
				new PostingDraftInput(null, null, null, null, null, null, null, null,
					null, null, null, null, null,
					null, null,
					true, 20, 10, AssignRule.ALL_MEMBERS, 1, 1,
					List.of(new InterviewTimeRangeInput(null, DATE, LocalTime.of(10, 0), LocalTime.of(11, 0), List.of())),
					null, null));
		}
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			new PostingDraftInput(null, null, null, null, null, null, null, null,
				null, null, null, null, null,
				null, null,
				null, null, null, null, null, null, null,
				true, List.of(new QuestionInput(null, QuestionType.TEXT, "자기소개", null, true, 500, null, 0))));
		postingService.publish(operator.getId(), posting.getId());
		return posting;
	}

	private User saveUser(String name) {
		String phone = "0108" + String.format("%07d", ThreadLocalRandom.current().nextInt(10_000_000));
		return userRepository.save(User.create(phone, "encoded", name, false, LocalDateTime.now()));
	}

}
