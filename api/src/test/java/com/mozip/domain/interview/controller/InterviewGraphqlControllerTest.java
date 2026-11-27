package com.mozip.domain.interview.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.data.ArgumentValue;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.jdbc.core.JdbcTemplate;

import com.mozip.TestcontainersConfiguration;
import com.mozip.domain.application.dto.AnswerInput;
import com.mozip.domain.application.dto.ApplicationInput;
import com.mozip.domain.application.entity.Application;
import com.mozip.domain.application.service.ApplicationService;
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
import com.mozip.domain.user.entity.Authority;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.security.cookie.AuthCookieManager;
import com.mozip.global.security.jwt.JwtProvider;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureHttpGraphQlTester
class InterviewGraphqlControllerTest {

	private static final String SELECT_INTERVIEW_TIMES = """
		mutation($applicationId: ID!, $availableTimes: [DateTime!]!) {
			selectInterviewTimes(applicationId: $applicationId, availableTimes: $availableTimes) {
				id
				status
				availableTimes
			}
		}
		""";

	private static final String GENERATE_INTERVIEW_TIMETABLE = """
		mutation($postingId: ID!) {
			generateInterviewTimetable(postingId: $postingId) {
				confirmedAt
				slots { startAt closed }
				interviews {
					startAt
					track
					location
					posting { id }
					interviewers { id }
					applicants { id }
				}
				unassignedApplicants { id }
				recentLocations
			}
		}
		""";

	private static final String INTERVIEW_TIMETABLE = """
		query($postingId: ID!) {
			interviewTimetable(postingId: $postingId) {
				interviews { applicants { id } }
				unassignedApplicants { id }
			}
		}
		""";

	@Autowired
	private HttpGraphQlTester graphQlTester;

	@Autowired
	private PostingService postingService;

	@Autowired
	private ApplicationService applicationService;

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
	private JwtProvider jwtProvider;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("로그인 없이 selectInterviewTimes를 부르면 COMMON_401이다")
	void selectInterviewTimesRequiresLogin() {
		graphQlTester.document(SELECT_INTERVIEW_TIMES)
			.variable("applicationId", "1")
			.variable("availableTimes", List.of())
			.execute()
			.errors()
			.satisfy(errors -> assertThat(errors.getFirst().getExtensions()).containsEntry("code", "COMMON_401"));
	}

	@Test
	@DisplayName("제출한 지원자가 selectInterviewTimes로 면접 가능 시간을 바꾸면 바뀐 값이 서울 오프셋으로 내려온다")
	void selectInterviewTimes() {
		LocalDate date = LocalDate.now().plusDays(10);
		User applicant = saveUser("지원자");
		Application application = submittedApplication(applicant, date);

		asUser(applicant).document(SELECT_INTERVIEW_TIMES)
			.variable("applicationId", application.getId())
			.variable("availableTimes", List.of(date.atTime(10, 30) + ":00+09:00"))
			.execute()
			.path("selectInterviewTimes.status").entity(String.class).isEqualTo("SUBMITTED")
			.path("selectInterviewTimes.availableTimes").entityList(String.class)
			.containsExactly(date.atTime(10, 30) + ":00+09:00");
	}

	@Test
	@DisplayName("다른 사람의 지원서에 selectInterviewTimes를 부르면 COMMON_403이다")
	void selectInterviewTimesOfOthers() {
		LocalDate date = LocalDate.now().plusDays(10);
		Application application = submittedApplication(saveUser("지원자"), date);

		asUser(saveUser("다른 지원자")).document(SELECT_INTERVIEW_TIMES)
			.variable("applicationId", application.getId())
			.variable("availableTimes", List.of(date.atTime(10, 30) + ":00+09:00"))
			.execute()
			.errors()
			.satisfy(errors -> assertThat(errors.getFirst().getExtensions()).containsEntry("code", "COMMON_403"));
	}

	@Test
	@DisplayName("타임테이블이 확정된 뒤 selectInterviewTimes를 부르면 INTERVIEW_409에 reason이 TIMETABLE_CONFIRMED다")
	void selectInterviewTimesAfterConfirm() {
		LocalDate date = LocalDate.now().plusDays(10);
		User applicant = saveUser("지원자");
		Application application = submittedApplication(applicant, date);
		jdbcTemplate.update("update postings set timetable_confirmed_at = now() where id = ?",
			application.getPosting().getId());

		asUser(applicant).document(SELECT_INTERVIEW_TIMES)
			.variable("applicationId", application.getId())
			.variable("availableTimes", List.of(date.atTime(10, 30) + ":00+09:00"))
			.execute()
			.errors()
			.satisfy(errors -> assertThat(errors.getFirst().getExtensions())
				.containsEntry("code", "INTERVIEW_409")
				.containsEntry("reason", "TIMETABLE_CONFIRMED"));
	}

	@Test
	@DisplayName("운영진이 generateInterviewTimetable을 부르면 서류 합격자가 고른 칸에 배정되고 interviewTimetable로 다시 읽힌다")
	void generateInterviewTimetable() {
		LocalDate date = LocalDate.now().plusDays(10);
		User operator = saveUser("운영진");
		Application application = submittedApplication(operator, saveUser("지원자"), date);
		String postingId = String.valueOf(application.getPosting().getId());
		String applicationId = String.valueOf(application.getId());
		// 서류 결과 확정은 서류 평가 이슈에서 생겨서 컬럼을 직접 채움
		jdbcTemplate.update("update applications set doc_result = 'PASS' where id = ?", application.getId());

		asUser(operator).document(GENERATE_INTERVIEW_TIMETABLE)
			.variable("postingId", postingId)
			.execute()
			.path("generateInterviewTimetable.confirmedAt").valueIsNull()
			.path("generateInterviewTimetable.slots[*].startAt").entityList(String.class)
			.containsExactly(date.atTime(10, 0) + ":00+09:00", date.atTime(10, 30) + ":00+09:00")
			.path("generateInterviewTimetable.interviews[*].startAt").entityList(String.class)
			.containsExactly(date.atTime(10, 0) + ":00+09:00")
			.path("generateInterviewTimetable.interviews[0].track").entity(Integer.class).isEqualTo(1)
			.path("generateInterviewTimetable.interviews[0].location").valueIsNull()
			.path("generateInterviewTimetable.interviews[0].posting.id").entity(String.class).isEqualTo(postingId)
			.path("generateInterviewTimetable.interviews[0].interviewers").entityList(Object.class).hasSize(0)
			.path("generateInterviewTimetable.interviews[0].applicants[*].id").entityList(String.class)
			.containsExactly(applicationId)
			.path("generateInterviewTimetable.unassignedApplicants").entityList(Object.class).hasSize(0)
			.path("generateInterviewTimetable.recentLocations").entityList(String.class).hasSize(0);

		asUser(operator).document(INTERVIEW_TIMETABLE)
			.variable("postingId", postingId)
			.execute()
			.path("interviewTimetable.interviews[0].applicants[*].id").entityList(String.class)
			.containsExactly(applicationId)
			.path("interviewTimetable.unassignedApplicants").entityList(Object.class).hasSize(0);
	}

	@Test
	@DisplayName("그 단체의 운영진이 아닌 회원이 interviewTimetable이나 generateInterviewTimetable을 부르면 COMMON_403이다")
	void interviewTimetableOfOtherOrganization() {
		Application application = submittedApplication(saveUser("지원자"), LocalDate.now().plusDays(10));
		String postingId = String.valueOf(application.getPosting().getId());
		User outsider = saveUser("외부인");

		asUser(outsider).document(INTERVIEW_TIMETABLE)
			.variable("postingId", postingId)
			.execute()
			.errors()
			.satisfy(errors -> assertThat(errors.getFirst().getExtensions()).containsEntry("code", "COMMON_403"));
		asUser(outsider).document(GENERATE_INTERVIEW_TIMETABLE)
			.variable("postingId", postingId)
			.execute()
			.errors()
			.satisfy(errors -> assertThat(errors.getFirst().getExtensions()).containsEntry("code", "COMMON_403"));
	}

	@Test
	@DisplayName("타임테이블이 확정된 뒤 generateInterviewTimetable을 부르면 INTERVIEW_409에 reason이 TIMETABLE_CONFIRMED다")
	void generateInterviewTimetableAfterConfirm() {
		User operator = saveUser("운영진");
		Application application = submittedApplication(operator, saveUser("지원자"), LocalDate.now().plusDays(10));
		jdbcTemplate.update("update postings set timetable_confirmed_at = now() where id = ?",
			application.getPosting().getId());

		asUser(operator).document(GENERATE_INTERVIEW_TIMETABLE)
			.variable("postingId", String.valueOf(application.getPosting().getId()))
			.execute()
			.errors()
			.satisfy(errors -> assertThat(errors.getFirst().getExtensions())
				.containsEntry("code", "INTERVIEW_409")
				.containsEntry("reason", "TIMETABLE_CONFIRMED"));
	}

	private Application submittedApplication(User applicant, LocalDate date) {
		return submittedApplication(saveUser("운영진"), applicant, date);
	}

	// 10:00~11:00에 면접 20분, 쉬는 시간 10분이라 블록은 10:00, 10:30. 10:00을 골라 제출해 둠
	private Application submittedApplication(User operator, User applicant, LocalDate date) {
		School school = schoolRepository.save(
			School.create("면접 시간 테스트대학교 " + ThreadLocalRandom.current().nextInt(1_000_000), null));
		jdbcTemplate.update("update users set school_id = ? where id = ?", school.getId(), applicant.getId());
		Organization organization = organizationRepository.save(
			Organization.create("면접 시간 테스트 단체 " + ThreadLocalRandom.current().nextInt(1_000_000),
				OrganizationType.CLUB, null, OrganizationScope.CAMPUS, null, true, null, null));
		memberRepository.save(Member.create(organization, operator, null, MemberRole.REPRESENTATIVE));

		Posting posting = postingService.saveDraft(operator.getId(), organization.getId(), null,
			new PostingDraftInput("면접 시간 공고 " + System.nanoTime(), "본문", ArgumentValue.ofNullable("2026-2"),
				ArgumentValue.ofNullable("서울"), ArgumentValue.ofNullable("무료"), true, false, null,
				null, null, LocalDateTime.now(), LocalDateTime.now().plusDays(7), null,
				true, null,
				true, 20, 10, AssignRule.ALL_MEMBERS, 1, 1,
				List.of(new InterviewTimeRangeInput(null, date, LocalTime.of(10, 0), LocalTime.of(11, 0), List.of())),
				true, List.of(new QuestionInput(null, QuestionType.TEXT, "자기소개", null, true, 500, null, 0))));
		postingService.publish(operator.getId(), posting.getId());
		Long questionId = questionRepository.findAllByPostingOrderBySortOrder(posting).get(0).getId();

		Application application = applicationService.saveDraft(applicant.getId(), posting.getId(),
			new ApplicationInput(null, List.of(new AnswerInput(questionId, "답변", null)),
				List.of(date.atTime(10, 0))));
		return applicationService.submit(applicant.getId(), application.getId());
	}

	private User saveUser(String name) {
		String phone = "0107" + String.format("%07d", ThreadLocalRandom.current().nextInt(10_000_000));
		return userRepository.save(User.create(phone, "encoded", name, false, LocalDateTime.now()));
	}

	private HttpGraphQlTester asUser(User user) {
		String accessToken = jwtProvider.createAccessToken(user.getId(), Authority.USER);
		return graphQlTester.mutate()
			.webTestClient(client -> client.defaultCookie(AuthCookieManager.ACCESS_TOKEN_COOKIE, accessToken))
			.build();
	}

}
