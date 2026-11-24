package com.mozip.domain.message.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

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
import com.mozip.domain.application.dto.AnswerInput;
import com.mozip.domain.application.dto.ApplicationInput;
import com.mozip.domain.application.entity.Application;
import com.mozip.domain.application.service.ApplicationService;
import com.mozip.domain.message.dto.EmailWarning;
import com.mozip.domain.message.dto.Recipient;
import com.mozip.domain.message.dto.ResultMessagePanel;
import com.mozip.domain.message.dto.ResultMessages;
import com.mozip.domain.message.entity.Message;
import com.mozip.domain.message.entity.MessageDelivery;
import com.mozip.domain.message.entity.MessageDeliveryStatus;
import com.mozip.domain.message.entity.MessageStatus;
import com.mozip.domain.message.entity.MessageTarget;
import com.mozip.domain.message.entity.MessageType;
import com.mozip.domain.message.exception.MessageConflictReason;
import com.mozip.domain.message.exception.MessageErrorCode;
import com.mozip.domain.message.exception.MessageInputReason;
import com.mozip.domain.message.exception.MessageSendReason;
import com.mozip.domain.message.repository.MessageDeliveryRepository;
import com.mozip.domain.message.repository.MessageRepository;
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
import com.mozip.domain.posting.exception.PostingErrorCode;
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

// 서류 결과와 확정 시각, 연락 이메일은 바꾸는 메서드가 아직 없거나 다른 작업 범위라 SQL로 직접 넣음.
// SQL 앞에서 flush, 뒤에서 clear를 해 영속성 컨텍스트와 DB가 어긋나지 않게 함
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class MessageServiceTest {

	private static final MessageType TYPE = MessageType.DOC_RESULT;

	@Autowired
	private MessageService messageService;

	@Autowired
	private MessageRepository messageRepository;

	@Autowired
	private MessageDeliveryRepository messageDeliveryRepository;

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
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	private Long operatorId;
	private Long postingId;
	private Long questionId;
	private Long schoolId;

	@BeforeEach
	void setUp() {
		User operator = saveUser("운영진");
		Organization organization = organizationRepository.save(
			Organization.create("메시지 테스트 단체", OrganizationType.CLUB, null, OrganizationScope.CAMPUS, null, true, null,
				null));
		memberRepository.save(Member.create(organization, operator, null, MemberRole.REPRESENTATIVE));
		School school = schoolRepository.save(School.create("메시지 테스트대학교", "msgtest.ac.kr"));

		Posting posting = postingService.saveDraft(operator.getId(), organization.getId(), null, basicInput("메시지 공고"));
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(), withNoPosition());
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withQuestions(List.of(new QuestionInput(null, QuestionType.TEXT, "자기소개", null, true, 500, null, 0))));
		postingService.publish(operator.getId(), posting.getId());

		operatorId = operator.getId();
		postingId = posting.getId();
		questionId = questionRepository.findAllByPostingOrderBySortOrder(posting).get(0).getId();
		schoolId = school.getId();
	}

	@Test
	@DisplayName("서류 결과가 확정되기 전에는 조회, 저장, 발송 모두 MESSAGE_409(RESULT_NOT_CONFIRMED)다")
	void rejectsBeforeResultConfirmed() {
		submitApplication("합격자", "PASS", "pass@gmail.com");

		assertBusinessException(() -> messageService.getResultMessages(operatorId, postingId, TYPE),
			MessageErrorCode.CONFLICT, MessageConflictReason.RESULT_NOT_CONFIRMED);
		assertBusinessException(() -> messageService.save(operatorId, postingId, TYPE, MessageTarget.PASS, "제목", "본문"),
			MessageErrorCode.CONFLICT, MessageConflictReason.RESULT_NOT_CONFIRMED);
		assertBusinessException(() -> messageService.send(operatorId, postingId, TYPE, List.of(MessageTarget.PASS)),
			MessageErrorCode.CONFLICT, MessageConflictReason.RESULT_NOT_CONFIRMED);
		assertThat(messageRepository.findAll()).isEmpty();
	}

	@Test
	@DisplayName("운영진이 아니면 조회, 저장, 발송 모두 COMMON_403이고 없는 공고는 POSTING_404다")
	void rejectsNonOperator() {
		confirmDocResult();
		Long outsiderId = saveUser("외부인").getId();

		assertBusinessException(() -> messageService.getResultMessages(outsiderId, postingId, TYPE),
			CommonErrorCode.FORBIDDEN, null);
		assertBusinessException(() -> messageService.save(outsiderId, postingId, TYPE, MessageTarget.PASS, "제목", "본문"),
			CommonErrorCode.FORBIDDEN, null);
		assertBusinessException(() -> messageService.send(outsiderId, postingId, TYPE, List.of(MessageTarget.PASS)),
			CommonErrorCode.FORBIDDEN, null);
		assertBusinessException(() -> messageService.getResultMessages(operatorId, -1L, TYPE),
			PostingErrorCode.NOT_FOUND, null);
		assertThat(messageRepository.findAll()).isEmpty();
	}

	@Test
	@DisplayName("조회하면 합격과 불합격 패널에 받는 사람과 이메일 경고, 기본 문구가 나오고 저장 전에는 message가 null이다")
	void getsResultMessages() {
		Application pass = submitApplication("나합격", "PASS", "pass@gmail.com");
		Application typo = submitApplication("가오타", "PASS", "typo@gmai.com");
		Application missing = submitApplication("다없음", "PASS", null);
		Application fail = submitApplication("불합격자", "FAIL", "fail@msgtest.ac.kr");
		submitApplication("보류자", "HOLD", "hold@gmail.com");
		submitApplication("미정자", "PENDING", "pending@gmail.com");
		confirmDocResult();

		ResultMessages result = messageService.getResultMessages(operatorId, postingId, TYPE);

		ResultMessagePanel passPanel = result.pass();
		assertThat(result.posting().getId()).isEqualTo(postingId);
		assertThat(passPanel.target()).isEqualTo(MessageTarget.PASS);
		assertThat(passPanel.message()).isNull();
		assertThat(passPanel.template().subject()).contains("메시지 테스트 단체");
		assertThat(passPanel.template().body()).contains("{이름}");
		assertThat(passPanel.recipientCount()).isEqualTo(3);
		assertThat(passPanel.sendableCount()).isEqualTo(1);
		assertThat(passPanel.processedCount()).isZero();
		// 이름순
		assertThat(passPanel.recipients()).extracting(recipient -> recipient.application().getId())
			.containsExactly(typo.getId(), pass.getId(), missing.getId());
		assertThat(passPanel.recipients()).extracting(Recipient::emailWarning)
			.containsExactly(EmailWarning.SUSPICIOUS_DOMAIN, null, EmailWarning.MISSING);
		assertThat(passPanel.recipients()).extracting(Recipient::suggestedDomain)
			.containsExactly("gmail.com", null, null);
		assertThat(passPanel.recipients()).extracting(Recipient::email)
			.containsExactly("typo@gmai.com", "pass@gmail.com", null);
		assertThat(passPanel.recipients()).extracting(Recipient::delivery).containsOnlyNulls();

		ResultMessagePanel failPanel = result.fail();
		assertThat(failPanel.target()).isEqualTo(MessageTarget.FAIL);
		assertThat(failPanel.recipientCount()).isEqualTo(1);
		assertThat(failPanel.sendableCount()).isEqualTo(1);
		assertThat(failPanel.recipients().get(0).application().getId()).isEqualTo(fail.getId());
		assertThat(failPanel.template().body()).isNotEqualTo(passPanel.template().body());
	}

	@Test
	@DisplayName("저장하면 DRAFT가 생기고 같은 대상을 다시 저장하면 같은 행을 덮어쓴다")
	void savesAndOverwritesDraft() {
		confirmDocResult();

		Message created = messageService.save(operatorId, postingId, TYPE, MessageTarget.PASS, "제목", "본문");
		Message fail = messageService.save(operatorId, postingId, TYPE, MessageTarget.FAIL, "불합격 제목", "불합격 본문");
		Message resaved = messageService.save(operatorId, postingId, TYPE, MessageTarget.PASS, "새 제목", "새 본문");
		entityManager.flush();
		entityManager.clear();

		assertThat(resaved.getId()).isEqualTo(created.getId());
		assertThat(fail.getId()).isNotEqualTo(created.getId());
		assertThat(messageRepository.findAll()).hasSize(2);
		Message found = messageRepository.findById(created.getId()).orElseThrow();
		assertThat(found.getStatus()).isEqualTo(MessageStatus.DRAFT);
		assertThat(found.getSubject()).isEqualTo("새 제목");
		assertThat(found.getBody()).isEqualTo("새 본문");
		assertThat(found.getSender().getId()).isEqualTo(operatorId);

		ResultMessages result = messageService.getResultMessages(operatorId, postingId, TYPE);
		assertThat(result.pass().message().getId()).isEqualTo(created.getId());
		assertThat(result.fail().message().getId()).isEqualTo(fail.getId());
	}

	@Test
	@DisplayName("저장 입력이 잘못되면 COMMON_400이고 reason으로 어디가 틀렸는지 알려 준다")
	void rejectsInvalidSaveInput() {
		confirmDocResult();

		assertInvalidInput(() -> messageService.save(operatorId, postingId, MessageType.NOTICE, MessageTarget.PASS, "제목",
			"본문"), MessageInputReason.UNSUPPORTED_TYPE);
		assertInvalidInput(() -> messageService.save(operatorId, postingId, TYPE, MessageTarget.ALL, "제목", "본문"),
			MessageInputReason.INVALID_TARGET);
		assertInvalidInput(() -> messageService.save(operatorId, postingId, TYPE, MessageTarget.PASS, " ", "본문"),
			MessageInputReason.SUBJECT_REQUIRED);
		assertInvalidInput(() -> messageService.save(operatorId, postingId, TYPE, MessageTarget.PASS,
			"가".repeat(Message.SUBJECT_MAX_LENGTH + 1), "본문"), MessageInputReason.SUBJECT_TOO_LONG);
		assertInvalidInput(() -> messageService.save(operatorId, postingId, TYPE, MessageTarget.PASS, "제목", ""),
			MessageInputReason.BODY_REQUIRED);
		assertInvalidInput(() -> messageService.save(operatorId, postingId, TYPE, MessageTarget.PASS, "제목",
			"나".repeat(Message.BODY_MAX_LENGTH + 1)), MessageInputReason.BODY_TOO_LONG);
		assertThat(messageRepository.findAll()).isEmpty();

		Message atLimit = messageService.save(operatorId, postingId, TYPE, MessageTarget.PASS,
			"가".repeat(Message.SUBJECT_MAX_LENGTH), "나".repeat(Message.BODY_MAX_LENGTH));
		assertThat(atLimit.getId()).isNotNull();
	}

	@Test
	@DisplayName("발송 대상이 비었거나 중복이거나 ALL이면 COMMON_400이고, 다른 종류의 메시지도 COMMON_400이다")
	void rejectsInvalidSendInput() {
		confirmDocResult();

		assertInvalidInput(() -> messageService.send(operatorId, postingId, TYPE, List.of()),
			MessageInputReason.TARGETS_REQUIRED);
		assertInvalidInput(() -> messageService.send(operatorId, postingId, TYPE,
			List.of(MessageTarget.PASS, MessageTarget.PASS)), MessageInputReason.DUPLICATE_TARGET);
		assertInvalidInput(() -> messageService.send(operatorId, postingId, TYPE, List.of(MessageTarget.ALL)),
			MessageInputReason.INVALID_TARGET);
		assertInvalidInput(() -> messageService.send(operatorId, postingId, MessageType.FINAL_RESULT,
			List.of(MessageTarget.PASS)), MessageInputReason.UNSUPPORTED_TYPE);
		assertInvalidInput(() -> messageService.getResultMessages(operatorId, postingId, MessageType.INTERVIEW),
			MessageInputReason.UNSUPPORTED_TYPE);
	}

	@Test
	@DisplayName("저장한 초안이 없는 대상을 발송하면 MESSAGE_422(MESSAGE_NOT_SAVED)다")
	void rejectsSendWithoutDraft() {
		submitApplication("합격자", "PASS", "pass@gmail.com");
		confirmDocResult();

		assertBusinessException(() -> messageService.send(operatorId, postingId, TYPE, List.of(MessageTarget.PASS)),
			MessageErrorCode.NOT_SENDABLE, MessageSendReason.MESSAGE_NOT_SAVED);
		assertThat(messageDeliveryRepository.findAll()).isEmpty();
	}

	@Test
	@DisplayName("받는 사람이 없으면 NO_RECIPIENTS, 전부 이메일 경고면 NO_SENDABLE_RECIPIENTS다")
	void rejectsSendWithoutSendableRecipients() {
		submitApplication("오타", "FAIL", "typo@naver.con");
		submitApplication("없음", "FAIL", null);
		confirmDocResult();
		messageService.save(operatorId, postingId, TYPE, MessageTarget.PASS, "제목", "본문");
		Message fail = messageService.save(operatorId, postingId, TYPE, MessageTarget.FAIL, "제목", "본문");

		assertBusinessException(() -> messageService.send(operatorId, postingId, TYPE, List.of(MessageTarget.PASS)),
			MessageErrorCode.NOT_SENDABLE, MessageSendReason.NO_RECIPIENTS);
		assertBusinessException(() -> messageService.send(operatorId, postingId, TYPE, List.of(MessageTarget.FAIL)),
			MessageErrorCode.NOT_SENDABLE, MessageSendReason.NO_SENDABLE_RECIPIENTS);
		assertThat(statusOf(fail)).isEqualTo(MessageStatus.DRAFT);
		assertThat(messageDeliveryRepository.findAll()).isEmpty();
	}

	@Test
	@DisplayName("발송을 시작하면 SENDING이 되고 이메일 경고가 없는 받는 사람에게만 PENDING 전달 내역이 생긴다")
	void sendsOnlyToRecipientsWithoutWarning() {
		Application ok = submitApplication("가정상", "PASS", " pass@gmail.com ");
		Application typo = submitApplication("나오타", "PASS", "typo@gmai.com");
		Application missing = submitApplication("다없음", "PASS", null);
		submitApplication("불합격자", "FAIL", "fail@gmail.com");
		confirmDocResult();
		Message draft = messageService.save(operatorId, postingId, TYPE, MessageTarget.PASS, "제목", "본문");
		Message failDraft = messageService.save(operatorId, postingId, TYPE, MessageTarget.FAIL, "제목", "본문");

		ResultMessages result = messageService.send(operatorId, postingId, TYPE, List.of(MessageTarget.PASS));

		ResultMessagePanel passPanel = result.pass();
		assertThat(passPanel.message().getId()).isEqualTo(draft.getId());
		assertThat(passPanel.message().getStatus()).isEqualTo(MessageStatus.SENDING);
		assertThat(passPanel.message().getSentAt()).isNull();
		assertThat(passPanel.recipientCount()).isEqualTo(3);
		assertThat(passPanel.sendableCount()).isEqualTo(1);
		assertThat(passPanel.processedCount()).isZero();
		assertThat(passPanel.deliveredCount()).isZero();
		assertThat(passPanel.failedCount()).isZero();
		assertThat(passPanel.recipients()).extracting(recipient -> recipient.application().getId())
			.containsExactly(ok.getId(), typo.getId(), missing.getId());
		assertThat(passPanel.recipients().get(0).delivery().getStatus()).isEqualTo(MessageDeliveryStatus.PENDING);
		assertThat(passPanel.recipients().get(1).delivery()).isNull();
		assertThat(passPanel.recipients().get(2).delivery()).isNull();
		// 발송하지 않은 불합격 쪽은 그대로
		assertThat(result.fail().message().getStatus()).isEqualTo(MessageStatus.DRAFT);
		assertThat(result.fail().recipients().get(0).delivery()).isNull();

		entityManager.flush();
		entityManager.clear();
		assertThat(statusOf(draft)).isEqualTo(MessageStatus.SENDING);
		assertThat(statusOf(failDraft)).isEqualTo(MessageStatus.DRAFT);
		List<MessageDelivery> deliveries = messageDeliveryRepository.findAll();
		assertThat(deliveries).hasSize(1);
		assertThat(deliveries.get(0).getMessage().getId()).isEqualTo(draft.getId());
		assertThat(deliveries.get(0).getApplication().getId()).isEqualTo(ok.getId());
		assertThat(deliveries.get(0).getEmail()).isEqualTo("pass@gmail.com");
		assertThat(deliveries.get(0).getStatus()).isEqualTo(MessageDeliveryStatus.PENDING);
	}

	@Test
	@DisplayName("이미 발송을 시작한 메시지는 다시 발송하거나 수정하면 MESSAGE_409(ALREADY_SENT)다")
	void rejectsSecondSend() {
		submitApplication("합격자", "PASS", "pass@gmail.com");
		confirmDocResult();
		Message draft = messageService.save(operatorId, postingId, TYPE, MessageTarget.PASS, "제목", "본문");
		messageService.send(operatorId, postingId, TYPE, List.of(MessageTarget.PASS));

		assertBusinessException(() -> messageService.send(operatorId, postingId, TYPE, List.of(MessageTarget.PASS)),
			MessageErrorCode.CONFLICT, MessageConflictReason.ALREADY_SENT);
		assertBusinessException(() -> messageService.save(operatorId, postingId, TYPE, MessageTarget.PASS, "새 제목",
			"새 본문"), MessageErrorCode.CONFLICT, MessageConflictReason.ALREADY_SENT);

		entityManager.flush();
		entityManager.clear();
		assertThat(messageDeliveryRepository.findAll()).hasSize(1);
		assertThat(messageRepository.findById(draft.getId()).orElseThrow().getSubject()).isEqualTo("제목");
	}

	@Test
	@DisplayName("읽은 뒤 다른 요청이 먼저 발송을 시작했으면 조건부 UPDATE가 0건이라 MESSAGE_409(ALREADY_SENT)다")
	void rejectsSendWhenStatusChangedAfterRead() {
		submitApplication("합격자", "PASS", "pass@gmail.com");
		confirmDocResult();
		Message draft = messageService.save(operatorId, postingId, TYPE, MessageTarget.PASS, "제목", "본문");
		entityManager.flush();
		// 영속성 컨텍스트의 엔티티는 DRAFT로 둔 채 DB만 SENDING으로 바꿔 다른 요청이 끼어든 상황을 만듦
		jdbcTemplate.update("update messages set status = 'SENDING' where id = ?", draft.getId());

		assertBusinessException(() -> messageService.send(operatorId, postingId, TYPE, List.of(MessageTarget.PASS)),
			MessageErrorCode.CONFLICT, MessageConflictReason.ALREADY_SENT);
		assertThat(messageDeliveryRepository.findAll()).isEmpty();
	}

	@Test
	@DisplayName("합격과 불합격을 같이 발송하면 둘 다 SENDING이 되고, 한쪽이 조건을 못 채우면 아무 것도 바뀌지 않는다")
	void sendsBothTargetsOrNothing() {
		submitApplication("합격자", "PASS", "pass@gmail.com");
		submitApplication("불합격자", "FAIL", "fail@gmail.com");
		confirmDocResult();
		Message pass = messageService.save(operatorId, postingId, TYPE, MessageTarget.PASS, "제목", "본문");

		assertBusinessException(() -> messageService.send(operatorId, postingId, TYPE,
			List.of(MessageTarget.PASS, MessageTarget.FAIL)), MessageErrorCode.NOT_SENDABLE,
			MessageSendReason.MESSAGE_NOT_SAVED);
		assertThat(statusOf(pass)).isEqualTo(MessageStatus.DRAFT);
		assertThat(messageDeliveryRepository.findAll()).isEmpty();

		messageService.save(operatorId, postingId, TYPE, MessageTarget.FAIL, "제목", "본문");
		ResultMessages result = messageService.send(operatorId, postingId, TYPE,
			List.of(MessageTarget.PASS, MessageTarget.FAIL));

		assertThat(result.pass().message().getStatus()).isEqualTo(MessageStatus.SENDING);
		assertThat(result.fail().message().getStatus()).isEqualTo(MessageStatus.SENDING);
		assertThat(messageDeliveryRepository.findAll()).hasSize(2);
	}

	// DB에 실제로 들어간 상태를 봄
	private MessageStatus statusOf(Message message) {
		entityManager.flush();
		return MessageStatus.valueOf(
			jdbcTemplate.queryForObject("select status from messages where id = ?", String.class, message.getId()));
	}

	private void assertInvalidInput(ThrowingCallable call, MessageInputReason reason) {
		assertBusinessException(call, CommonErrorCode.INVALID_INPUT, reason);
	}

	private void assertBusinessException(ThrowingCallable call, ErrorCode errorCode, Enum<?> reason) {
		assertThatThrownBy(call)
			.isInstanceOf(BusinessException.class)
			.satisfies(e -> {
				assertThat(((BusinessException)e).getErrorCode()).isEqualTo(errorCode);
				assertThat(((BusinessException)e).getReason()).isEqualTo(reason == null ? null : reason.name());
			});
	}

	private void confirmDocResult() {
		entityManager.flush();
		jdbcTemplate.update("update postings set doc_result_confirmed_at = now() where id = ?", postingId);
		entityManager.clear();
	}

	// 학교 인증을 마친 지원자가 지원서를 제출하고, 서류 결과와 연락 이메일이 정해진 상태를 만듦
	private Application submitApplication(String name, String docResult, String contactEmail) {
		User applicant = saveUser(name);
		entityManager.flush();
		jdbcTemplate.update("update users set school_id = ?, contact_email = ? where id = ?", schoolId, contactEmail,
			applicant.getId());
		entityManager.clear();

		Application application = applicationService.saveDraft(applicant.getId(), postingId,
			new ApplicationInput(null, List.of(new AnswerInput(questionId, "답변", null)), null));
		applicationService.submit(applicant.getId(), application.getId());
		entityManager.flush();
		jdbcTemplate.update("update applications set doc_result = ? where id = ?", docResult, application.getId());
		entityManager.clear();
		return application;
	}

	private User saveUser(String name) {
		String phone = "0108" + String.format("%07d", ThreadLocalRandom.current().nextInt(10_000_000));
		return userRepository.save(User.create(phone, "encoded", name, false, LocalDateTime.now()));
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
