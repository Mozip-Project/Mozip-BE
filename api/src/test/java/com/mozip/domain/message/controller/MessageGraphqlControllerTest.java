package com.mozip.domain.message.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
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
import com.mozip.domain.posting.dto.PostingDraftInput;
import com.mozip.domain.posting.dto.QuestionInput;
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

// 서비스 테스트로는 못 보는 것: 스키마와 컨트롤러 매핑이 맞는지, 중첩 필드(@BatchMapping)가 트랜잭션 밖에서 풀리는지,
// 에러 코드와 reason이 응답 extensions까지 나가는지. 롤백이 안 되므로 만든 행은 @AfterEach에서 지움
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureHttpGraphQlTester
class MessageGraphqlControllerTest {

	private static final String RESULT_MESSAGES_FIELDS = """
		posting { id }
		pass {
			target
			message { id status subject body updatedAt sentAt posting { id } sender { id } deliveries { id status email application { id } } }
			template { subject body }
			recipientCount sendableCount processedCount deliveredCount failedCount
			recipients { application { id } email emailWarning suggestedDomain delivery { id status email } }
		}
		fail {
			target
			message { id status }
			template { subject body }
			recipientCount sendableCount
			recipients { application { id } email emailWarning suggestedDomain delivery { id } }
		}
		""";

	private static final String RESULT_MESSAGES_QUERY = """
		query($postingId: ID!) {
			resultMessages(postingId: $postingId, type: DOC_RESULT) {
		""" + RESULT_MESSAGES_FIELDS + """
			}
		}
		""";

	private static final String SAVE_MESSAGE_MUTATION = """
		mutation($postingId: ID!, $target: MessageTarget!, $subject: String!, $body: String!) {
			saveMessage(postingId: $postingId, type: DOC_RESULT, target: $target, subject: $subject, body: $body) {
				id status type target subject body updatedAt sender { id }
			}
		}
		""";

	private static final String SEND_MESSAGES_MUTATION = """
		mutation($postingId: ID!, $targets: [MessageTarget!]!) {
			sendMessages(postingId: $postingId, type: DOC_RESULT, targets: $targets) {
		""" + RESULT_MESSAGES_FIELDS + """
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

	private final List<Long> userIds = new ArrayList<>();
	private Long postingId;
	private Long organizationId;
	private Long schoolId;
	private Long questionId;

	@AfterEach
	void cleanUp() {
		if (postingId != null) {
			jdbcTemplate.update(
				"delete from message_deliveries where message_id in (select id from messages where posting_id = ?)",
				postingId);
			jdbcTemplate.update("delete from messages where posting_id = ?", postingId);
			jdbcTemplate.update(
				"delete from answers where application_id in (select id from applications where posting_id = ?)",
				postingId);
			jdbcTemplate.update("delete from applications where posting_id = ?", postingId);
			jdbcTemplate.update("delete from questions where posting_id = ?", postingId);
			jdbcTemplate.update("delete from postings where id = ?", postingId);
		}
		if (organizationId != null) {
			jdbcTemplate.update("delete from members where organization_id = ?", organizationId);
			jdbcTemplate.update("delete from organizations where id = ?", organizationId);
		}
		userIds.forEach(userId -> jdbcTemplate.update("delete from users where id = ?", userId));
		if (schoolId != null) {
			jdbcTemplate.update("delete from schools where id = ?", schoolId);
		}
	}

	@Test
	@DisplayName("로그인 없이 resultMessages를 부르면 COMMON_401이다")
	void resultMessagesRequiresLogin() {
		graphQlTester.document("""
			query { resultMessages(postingId: "1", type: DOC_RESULT) { posting { id } } }
			""")
			.execute()
			.errors()
			.satisfy(errors -> assertThat(errors.getFirst().getExtensions()).containsEntry("code", "COMMON_401"));
	}

	@Test
	@DisplayName("운영진이 조회하고 저장한 뒤 발송을 시작하면 SENDING이 되고, 다시 조회해도 같은 결과가 나온다")
	void queriesSavesAndStartsSending() {
		User operator = saveUser("운영진");
		preparePosting(operator);
		Application pass = submitApplication("가합격", "PASS", "pass@gmail.com");
		Application typo = submitApplication("나오타", "PASS", "typo@gmai.com");
		Application fail = submitApplication("불합격자", "FAIL", "fail@naver.com");
		HttpGraphQlTester asOperator = asUser(operator);

		// 서류 결과 확정 전
		asOperator.document(RESULT_MESSAGES_QUERY)
			.variable("postingId", postingId)
			.execute()
			.errors()
			.satisfy(errors -> {
				assertThat(errors.getFirst().getExtensions()).containsEntry("code", "MESSAGE_409");
				assertThat(errors.getFirst().getExtensions()).containsEntry("reason", "RESULT_NOT_CONFIRMED");
			});
		jdbcTemplate.update("update postings set doc_result_confirmed_at = now() where id = ?", postingId);

		// 조회: 저장 전이라 message는 null, 기본 문구와 받는 사람이 나옴
		GraphQlTester.Response before = asOperator.document(RESULT_MESSAGES_QUERY)
			.variable("postingId", postingId)
			.execute();
		before.path("resultMessages.posting.id").entity(String.class).isEqualTo(postingId.toString());
		before.path("resultMessages.pass.message").valueIsNull();
		before.path("resultMessages.pass.template.subject").entity(String.class)
			.satisfies(subject -> assertThat(subject).isNotBlank());
		before.path("resultMessages.pass.recipientCount").entity(Integer.class).isEqualTo(2);
		before.path("resultMessages.pass.sendableCount").entity(Integer.class).isEqualTo(1);
		before.path("resultMessages.pass.recipients[*].application.id").entityList(String.class)
			.containsExactly(pass.getId().toString(), typo.getId().toString());
		before.path("resultMessages.pass.recipients[1].emailWarning").entity(String.class)
			.isEqualTo("SUSPICIOUS_DOMAIN");
		before.path("resultMessages.pass.recipients[1].suggestedDomain").entity(String.class).isEqualTo("gmail.com");
		before.path("resultMessages.pass.recipients[0].emailWarning").valueIsNull();
		before.path("resultMessages.fail.recipients[0].application.id").entity(String.class)
			.isEqualTo(fail.getId().toString());

		// 저장: 두 번 저장해도 같은 id
		String messageId = saveMessage(asOperator, "PASS", "제목", "본문")
			.path("saveMessage.id").entity(String.class).get();
		GraphQlTester.Response resaved = saveMessage(asOperator, "PASS", "새 제목", "{이름}님 합격입니다.");
		resaved.path("saveMessage.id").entity(String.class).isEqualTo(messageId);
		resaved.path("saveMessage.status").entity(String.class).isEqualTo("DRAFT");
		resaved.path("saveMessage.subject").entity(String.class).isEqualTo("새 제목");
		resaved.path("saveMessage.sender.id").entity(String.class).isEqualTo(operator.getId().toString());

		// 입력 오류와 초안 없는 대상 발송
		asOperator.document(SAVE_MESSAGE_MUTATION)
			.variable("postingId", postingId)
			.variable("target", "ALL")
			.variable("subject", "제목")
			.variable("body", "본문")
			.execute()
			.errors()
			.satisfy(errors -> {
				assertThat(errors.getFirst().getExtensions()).containsEntry("code", "COMMON_400");
				assertThat(errors.getFirst().getExtensions()).containsEntry("reason", "INVALID_TARGET");
			});
		asOperator.document(SEND_MESSAGES_MUTATION)
			.variable("postingId", postingId)
			.variable("targets", List.of("PASS", "FAIL"))
			.execute()
			.errors()
			.satisfy(errors -> {
				assertThat(errors.getFirst().getExtensions()).containsEntry("code", "MESSAGE_422");
				assertThat(errors.getFirst().getExtensions()).containsEntry("reason", "MESSAGE_NOT_SAVED");
			});

		// 발송 시작
		GraphQlTester.Response sent = asOperator.document(SEND_MESSAGES_MUTATION)
			.variable("postingId", postingId)
			.variable("targets", List.of("PASS"))
			.execute();
		assertSending(sent, "sendMessages", messageId, pass, operator);

		// 다시 조회해도 같은 모양
		GraphQlTester.Response after = asOperator.document(RESULT_MESSAGES_QUERY)
			.variable("postingId", postingId)
			.execute();
		assertSending(after, "resultMessages", messageId, pass, operator);

		// 두 번째 발송과 발송 뒤 수정은 거부
		asOperator.document(SEND_MESSAGES_MUTATION)
			.variable("postingId", postingId)
			.variable("targets", List.of("PASS"))
			.execute()
			.errors()
			.satisfy(errors -> {
				assertThat(errors.getFirst().getExtensions()).containsEntry("code", "MESSAGE_409");
				assertThat(errors.getFirst().getExtensions()).containsEntry("reason", "ALREADY_SENT");
			});
		asOperator.document(SAVE_MESSAGE_MUTATION)
			.variable("postingId", postingId)
			.variable("target", "PASS")
			.variable("subject", "또 수정")
			.variable("body", "본문")
			.execute()
			.errors()
			.satisfy(errors -> assertThat(errors.getFirst().getExtensions()).containsEntry("reason", "ALREADY_SENT"));
		assertThat(jdbcTemplate.queryForObject(
			"select count(*) from message_deliveries where message_id = ?", Integer.class, Long.valueOf(messageId)))
			.isEqualTo(1);
	}

	@Test
	@DisplayName("운영진이 아닌 사용자가 조회, 저장, 발송을 부르면 COMMON_403이다")
	void rejectsNonOperator() {
		User operator = saveUser("운영진");
		preparePosting(operator);
		jdbcTemplate.update("update postings set doc_result_confirmed_at = now() where id = ?", postingId);
		HttpGraphQlTester asOutsider = asUser(saveUser("외부인"));

		asOutsider.document(RESULT_MESSAGES_QUERY)
			.variable("postingId", postingId)
			.execute()
			.errors()
			.satisfy(errors -> assertThat(errors.getFirst().getExtensions()).containsEntry("code", "COMMON_403"));
		saveMessage(asOutsider, "PASS", "제목", "본문")
			.errors()
			.satisfy(errors -> assertThat(errors.getFirst().getExtensions()).containsEntry("code", "COMMON_403"));
		asOutsider.document(SEND_MESSAGES_MUTATION)
			.variable("postingId", postingId)
			.variable("targets", List.of("PASS"))
			.execute()
			.errors()
			.satisfy(errors -> assertThat(errors.getFirst().getExtensions()).containsEntry("code", "COMMON_403"));
		assertThat(jdbcTemplate.queryForObject("select count(*) from messages where posting_id = ?", Integer.class,
			postingId)).isZero();
	}

	private void assertSending(GraphQlTester.Response response, String root, String messageId, Application pass,
		User operator) {
		response.path(root + ".pass.message.id").entity(String.class).isEqualTo(messageId);
		response.path(root + ".pass.message.status").entity(String.class).isEqualTo("SENDING");
		response.path(root + ".pass.message.sentAt").valueIsNull();
		response.path(root + ".pass.message.posting.id").entity(String.class).isEqualTo(postingId.toString());
		response.path(root + ".pass.message.sender.id").entity(String.class).isEqualTo(operator.getId().toString());
		response.path(root + ".pass.message.deliveries").entityList(Object.class).hasSize(1);
		response.path(root + ".pass.message.deliveries[0].status").entity(String.class).isEqualTo("PENDING");
		response.path(root + ".pass.message.deliveries[0].email").entity(String.class).isEqualTo("pass@gmail.com");
		response.path(root + ".pass.message.deliveries[0].application.id").entity(String.class)
			.isEqualTo(pass.getId().toString());
		response.path(root + ".pass.recipientCount").entity(Integer.class).isEqualTo(2);
		response.path(root + ".pass.sendableCount").entity(Integer.class).isEqualTo(1);
		response.path(root + ".pass.processedCount").entity(Integer.class).isEqualTo(0);
		response.path(root + ".pass.recipients[0].delivery.status").entity(String.class).isEqualTo("PENDING");
		response.path(root + ".pass.recipients[1].delivery").valueIsNull();
		response.path(root + ".fail.message").valueIsNull();
		response.path(root + ".fail.recipients[0].delivery").valueIsNull();
	}

	private GraphQlTester.Response saveMessage(HttpGraphQlTester tester, String target, String subject, String body) {
		return tester.document(SAVE_MESSAGE_MUTATION)
			.variable("postingId", postingId)
			.variable("target", target)
			.variable("subject", subject)
			.variable("body", body)
			.execute();
	}

	private void preparePosting(User operator) {
		School school = schoolRepository.save(
			School.create("메시지 테스트대학교 " + ThreadLocalRandom.current().nextInt(1_000_000), null));
		schoolId = school.getId();
		Organization organization = organizationRepository.save(
			Organization.create("메시지 테스트 단체 " + ThreadLocalRandom.current().nextInt(1_000_000),
				OrganizationType.CLUB, null, OrganizationScope.CAMPUS, null, true, null, null));
		organizationId = organization.getId();
		memberRepository.save(Member.create(organization, operator, null, MemberRole.REPRESENTATIVE));

		Posting posting = postingService.saveDraft(operator.getId(), organization.getId(), null,
			basicInput("메시지 공고 " + System.nanoTime()));
		postingId = posting.getId();
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(), withNoPosition());
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withQuestions(List.of(new QuestionInput(null, QuestionType.TEXT, "자기소개", null, true, 500, null, 0))));
		postingService.publish(operator.getId(), posting.getId());
		questionId = questionRepository.findAllByPostingOrderBySortOrder(posting).get(0).getId();
	}

	private Application submitApplication(String name, String docResult, String contactEmail) {
		User applicant = saveUser(name);
		jdbcTemplate.update("update users set school_id = ?, contact_email = ? where id = ?", schoolId, contactEmail,
			applicant.getId());
		Application application = applicationService.saveDraft(applicant.getId(), postingId,
			new ApplicationInput(null, List.of(new AnswerInput(questionId, "답변", null)), null));
		applicationService.submit(applicant.getId(), application.getId());
		jdbcTemplate.update("update applications set doc_result = ? where id = ?", docResult, application.getId());
		return application;
	}

	private User saveUser(String name) {
		String phone = "0107" + String.format("%07d", ThreadLocalRandom.current().nextInt(10_000_000));
		User user = userRepository.save(User.create(phone, "encoded", name, false, LocalDateTime.now()));
		userIds.add(user.getId());
		return user;
	}

	private HttpGraphQlTester asUser(User user) {
		String accessToken = jwtProvider.createAccessToken(user.getId(), Authority.USER);
		return graphQlTester.mutate()
			.webTestClient(client -> client.defaultCookie(AuthCookieManager.ACCESS_TOKEN_COOKIE, accessToken))
			.build();
	}

	private PostingDraftInput basicInput(String title) {
		return new PostingDraftInput(title, "본문", "2026-2", "서울", "무료", true, false, null,
			null, null, LocalDateTime.now(), LocalDateTime.now().plusDays(7), null,
			null, null,
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

	private PostingDraftInput withQuestions(List<QuestionInput> questions) {
		return new PostingDraftInput(null, null, null, null, null, null, null, null,
			null, null, null, null, null,
			null, null,
			null, null, null, null, null, null, null, null,
			true, questions,
			null);
	}

}
