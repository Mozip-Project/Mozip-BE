package com.mozip.domain.message.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.domain.application.entity.Application;
import com.mozip.domain.application.entity.ApplicationStatus;
import com.mozip.domain.application.entity.DocResult;
import com.mozip.domain.application.repository.ApplicationRepository;
import com.mozip.domain.message.dto.EmailCheck;
import com.mozip.domain.message.dto.Recipient;
import com.mozip.domain.message.dto.ResultMessagePanel;
import com.mozip.domain.message.dto.ResultMessages;
import com.mozip.domain.message.entity.Message;
import com.mozip.domain.message.entity.MessageDelivery;
import com.mozip.domain.message.entity.MessageDeliveryStatus;
import com.mozip.domain.message.entity.MessageTarget;
import com.mozip.domain.message.entity.MessageType;
import com.mozip.domain.message.exception.MessageConflictReason;
import com.mozip.domain.message.exception.MessageErrorCode;
import com.mozip.domain.message.exception.MessageInputReason;
import com.mozip.domain.message.exception.MessageSendReason;
import com.mozip.domain.message.repository.MessageDeliveryRepository;
import com.mozip.domain.message.repository.MessageRepository;
import com.mozip.domain.organization.service.OrganizationAccessChecker;
import com.mozip.domain.posting.entity.Position;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.exception.PostingErrorCode;
import com.mozip.domain.posting.repository.PositionRepository;
import com.mozip.domain.posting.repository.PostingRepository;
import com.mozip.domain.school.entity.School;
import com.mozip.domain.school.repository.SchoolRepository;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

// 서류 결과 안내 메시지의 조회, 초안 저장, 발송 시작. 실제 메일 발송과 SENT 전환은 worker 몫
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageService {

	private static final List<MessageTarget> RESULT_TARGETS = List.of(MessageTarget.PASS, MessageTarget.FAIL);

	private final MessageRepository messageRepository;
	private final MessageDeliveryRepository messageDeliveryRepository;
	private final PostingRepository postingRepository;
	private final ApplicationRepository applicationRepository;
	private final PositionRepository positionRepository;
	private final UserRepository userRepository;
	private final SchoolRepository schoolRepository;
	private final OrganizationAccessChecker organizationAccessChecker;
	private final MessageNotifier messageNotifier;
	private final MessageMailQueue messageMailQueue;
	private final EntityManager entityManager;

	public ResultMessages getResultMessages(Long userId, Long postingId, MessageType type) {
		Posting posting = findPostingForOperator(userId, postingId);
		requireSupportedType(type);
		requireResultConfirmed(posting);
		return buildResultMessages(posting, type, findCandidates(posting));
	}

	@Transactional
	public Message save(Long userId, Long postingId, MessageType type, MessageTarget target, String subject,
		String body) {
		Posting posting = findPostingForOperator(userId, postingId);
		requireSupportedType(type);
		requireResultTarget(target);
		validateContent(subject, body);
		requireResultConfirmed(posting);

		User sender = userRepository.getReferenceById(userId);
		Message existing = messageRepository.findForUpdate(posting, type, target).orElse(null);
		if (existing == null) {
			return createDraft(posting, sender, type, target, subject, body);
		}
		if (!existing.isDraft()) {
			throw alreadySent(existing);
		}
		existing.updateDraft(sender, subject, body);
		return existing;
	}

	@Transactional
	public ResultMessages send(Long userId, Long postingId, MessageType type, List<MessageTarget> targets) {
		Posting posting = findPostingForOperator(userId, postingId);
		requireSupportedType(type);
		validateTargets(targets);
		requireResultConfirmed(posting);

		Map<MessageTarget, Message> messages = findMessages(posting, type);
		Map<MessageTarget, List<Candidate>> candidates = findCandidates(posting);
		// 대상 전부를 먼저 검사함. 한 대상이라도 조건을 못 채우면 아무 것도 바꾸지 않고 끝냄
		for (MessageTarget target : targets) {
			requireSendable(posting, target, messages.get(target), candidates.get(target));
		}

		User sender = userRepository.getReferenceById(userId);
		for (MessageTarget target : targets) {
			Message message = messages.get(target);
			// 조건부 UPDATE가 0건이면 다른 요청이 먼저 발송을 시작한 것. 예외로 앞 대상의 전환까지 롤백됨
			if (messageRepository.startSending(message.getId(), sender, LocalDateTime.now()) == 0) {
				throw alreadySent(message);
			}
			// JPQL UPDATE는 이미 읽어 둔 엔티티를 바꾸지 않아서 DB 값으로 다시 맞춤
			entityManager.refresh(message);

			List<Candidate> sendable = candidates.get(target).stream().filter(Candidate::sendable).toList();
			List<MessageDelivery> deliveries = messageDeliveryRepository.saveAll(sendable.stream()
				.map(candidate -> MessageDelivery.create(message, candidate.application(), candidate.email()))
				.toList());
			messageNotifier.notifyResult(message, sendable.stream().map(Candidate::application).toList());
			messageMailQueue.enqueue(message, deliveries);
		}
		return buildResultMessages(posting, type, candidates);
	}

	// 같은 (공고, type, target)의 첫 저장이 동시에 들어오면 uk_messages_posting_id_type_target을 어김.
	// 그 트랜잭션은 더 쓸 수 없어 500 대신 다시 요청하면 되는 409로 바꿔서 던짐
	private Message createDraft(Posting posting, User sender, MessageType type, MessageTarget target, String subject,
		String body) {
		try {
			return messageRepository.saveAndFlush(Message.create(posting, sender, type, target, subject, body));
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(MessageErrorCode.CONFLICT, MessageConflictReason.CONCURRENT_SAVE)
				.addContext("postingId", posting.getId())
				.addContext("type", type)
				.addContext("target", target);
		}
	}

	private ResultMessages buildResultMessages(Posting posting, MessageType type,
		Map<MessageTarget, List<Candidate>> candidates) {
		Map<MessageTarget, Message> messages = findMessages(posting, type);
		Map<Long, Map<Long, MessageDelivery>> deliveries = messageDeliveryRepository
			.findAllByMessageInOrderById(messages.values())
			.stream()
			.collect(Collectors.groupingBy(delivery -> delivery.getMessage().getId(),
				Collectors.toMap(delivery -> delivery.getApplication().getId(), Function.identity())));
		Map<MessageTarget, ResultMessagePanel> panels = new EnumMap<>(MessageTarget.class);
		for (MessageTarget target : RESULT_TARGETS) {
			Message message = messages.get(target);
			Map<Long, MessageDelivery> deliveryByApplicationId = message == null ? Map.of()
				: deliveries.getOrDefault(message.getId(), Map.of());
			panels.put(target, buildPanel(posting, target, message, candidates.get(target), deliveryByApplicationId));
		}
		return new ResultMessages(posting, panels.get(MessageTarget.PASS), panels.get(MessageTarget.FAIL));
	}

	private ResultMessagePanel buildPanel(Posting posting, MessageTarget target, Message message,
		List<Candidate> candidates, Map<Long, MessageDelivery> deliveryByApplicationId) {
		List<Recipient> recipients = candidates.stream()
			.map(candidate -> new Recipient(candidate.application(), candidate.email(), candidate.check().warning(),
				candidate.check().suggestedDomain(), deliveryByApplicationId.get(candidate.application().getId())))
			.toList();
		int delivered = countByStatus(deliveryByApplicationId, MessageDeliveryStatus.SUCCESS);
		int failed = countByStatus(deliveryByApplicationId, MessageDeliveryStatus.FAILED);
		return new ResultMessagePanel(target, message,
			ResultMessageTemplates.docResult(target, posting.getOrganization().getName()),
			candidates.size(), (int)candidates.stream().filter(Candidate::sendable).count(),
			delivered + failed, delivered, failed, recipients);
	}

	private static int countByStatus(Map<Long, MessageDelivery> deliveries, MessageDeliveryStatus status) {
		return (int)deliveries.values().stream().filter(delivery -> delivery.getStatus() == status).count();
	}

	private Map<MessageTarget, Message> findMessages(Posting posting, MessageType type) {
		return messageRepository.findAllByPostingAndType(posting, type).stream()
			.collect(Collectors.toMap(Message::getTarget, Function.identity()));
	}

	// 받는 사람 = 제출된 지원서 중 서류 결과가 PASS 또는 FAIL. 지원자용 리졸버는 결과 발표 전까지 값을 가리지만
	// 운영진 화면이라 엔티티의 실제 값을 씀. 이름순, 동명이인은 지원서 id순
	private Map<MessageTarget, List<Candidate>> findCandidates(Posting posting) {
		List<Application> applications = applicationRepository
			.findAllByPostingAndStatus(posting, ApplicationStatus.SUBMITTED)
			.stream()
			.filter(application -> targetOf(application.getDocResult()) != null)
			.toList();
		// 지원자와 포지션을 한 번에 읽어 둠. 행마다 지연 로딩 쿼리가 나가지 않게 하려는 것
		userRepository.findAllById(applications.stream().map(a -> a.getUser().getId()).distinct().toList());
		positionRepository.findAllById(applications.stream()
			.map(Application::getPosition)
			.filter(Objects::nonNull)
			.map(Position::getId)
			.distinct()
			.toList());

		EmailWarningChecker checker = new EmailWarningChecker(
			schoolRepository.findAll().stream().map(School::getEmailDomain).toList());
		Map<MessageTarget, List<Candidate>> candidates = new EnumMap<>(MessageTarget.class);
		RESULT_TARGETS.forEach(target -> candidates.put(target, new ArrayList<>()));
		applications.stream()
			.sorted(Comparator.comparing((Application a) -> a.getUser().getName()).thenComparing(Application::getId))
			.forEach(application -> {
				String email = normalizeEmail(application.getUser().getContactEmail());
				candidates.get(targetOf(application.getDocResult()))
					.add(new Candidate(application, email, checker.check(email)));
			});
		return candidates;
	}

	private static MessageTarget targetOf(DocResult docResult) {
		return switch (docResult) {
			case PASS -> MessageTarget.PASS;
			case FAIL -> MessageTarget.FAIL;
			default -> null;
		};
	}

	// 앞뒤 공백을 떼고 빈 값은 주소 없음(null)으로 봄
	private static String normalizeEmail(String contactEmail) {
		if (contactEmail == null || contactEmail.isBlank()) {
			return null;
		}
		return contactEmail.trim();
	}

	private void requireSendable(Posting posting, MessageTarget target, Message message, List<Candidate> candidates) {
		if (message == null) {
			throw notSendable(MessageSendReason.MESSAGE_NOT_SAVED, posting, target);
		}
		if (!message.isDraft()) {
			throw alreadySent(message);
		}
		if (message.getSubject().isBlank() || message.getBody().isBlank()) {
			throw notSendable(MessageSendReason.EMPTY_MESSAGE, posting, target);
		}
		if (candidates.isEmpty()) {
			throw notSendable(MessageSendReason.NO_RECIPIENTS, posting, target);
		}
		if (candidates.stream().noneMatch(Candidate::sendable)) {
			throw notSendable(MessageSendReason.NO_SENDABLE_RECIPIENTS, posting, target);
		}
	}

	private static BusinessException notSendable(MessageSendReason reason, Posting posting, MessageTarget target) {
		return new BusinessException(MessageErrorCode.NOT_SENDABLE, reason)
			.addContext("postingId", posting.getId())
			.addContext("target", target);
	}

	private static BusinessException alreadySent(Message message) {
		return new BusinessException(MessageErrorCode.CONFLICT, MessageConflictReason.ALREADY_SENT)
			.addContext("messageId", message.getId());
	}

	// 없으면 POSTING_404, 그 공고 단체의 운영진이 아니면 COMMON_403
	private Posting findPostingForOperator(Long userId, Long postingId) {
		Posting posting = postingRepository.findByIdWithOrganization(postingId)
			.orElseThrow(() -> new BusinessException(PostingErrorCode.NOT_FOUND).addContext("postingId", postingId));
		Long organizationId = posting.getOrganization().getId();
		if (!organizationAccessChecker.isOperatorOf(userId, organizationId)) {
			throw new BusinessException(CommonErrorCode.FORBIDDEN)
				.addContext("userId", userId)
				.addContext("organizationId", organizationId);
		}
		return posting;
	}

	// 면접 안내, 최종 결과, 공지는 화면이 정해진 뒤에 엶
	private static void requireSupportedType(MessageType type) {
		if (type != MessageType.DOC_RESULT) {
			throw invalidInput(MessageInputReason.UNSUPPORTED_TYPE).addContext("type", type);
		}
	}

	private static void requireResultTarget(MessageTarget target) {
		if (!RESULT_TARGETS.contains(target)) {
			throw invalidInput(MessageInputReason.INVALID_TARGET).addContext("target", target);
		}
	}

	private static void validateTargets(List<MessageTarget> targets) {
		if (targets == null || targets.isEmpty()) {
			throw invalidInput(MessageInputReason.TARGETS_REQUIRED);
		}
		if (new HashSet<>(targets).size() != targets.size()) {
			throw invalidInput(MessageInputReason.DUPLICATE_TARGET).addContext("targets", targets);
		}
		targets.forEach(MessageService::requireResultTarget);
	}

	private static void validateContent(String subject, String body) {
		if (subject == null || subject.isBlank()) {
			throw invalidInput(MessageInputReason.SUBJECT_REQUIRED);
		}
		if (subject.length() > Message.SUBJECT_MAX_LENGTH) {
			throw invalidInput(MessageInputReason.SUBJECT_TOO_LONG).addContext("length", subject.length());
		}
		if (body == null || body.isBlank()) {
			throw invalidInput(MessageInputReason.BODY_REQUIRED);
		}
		if (body.length() > Message.BODY_MAX_LENGTH) {
			throw invalidInput(MessageInputReason.BODY_TOO_LONG).addContext("length", body.length());
		}
	}

	private static BusinessException invalidInput(MessageInputReason reason) {
		return new BusinessException(CommonErrorCode.INVALID_INPUT, reason);
	}

	private static void requireResultConfirmed(Posting posting) {
		if (posting.getDocResultConfirmedAt() == null) {
			throw new BusinessException(MessageErrorCode.CONFLICT, MessageConflictReason.RESULT_NOT_CONFIRMED)
				.addContext("postingId", posting.getId());
		}
	}

	// 받는 사람 후보 한 명과 그 주소의 이메일 경고 판정
	private record Candidate(Application application, String email, EmailCheck check) {

		boolean sendable() {
			return check.sendable();
		}

	}

}
