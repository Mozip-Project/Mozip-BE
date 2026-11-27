package com.mozip.domain.notification.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.domain.notification.entity.Notification;
import com.mozip.domain.notification.entity.NotificationType;
import com.mozip.domain.notification.event.NotificationCreatedEvent;
import com.mozip.domain.notification.exception.NotificationErrorCode;
import com.mozip.domain.notification.repository.NotificationRepository;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.repository.OrganizationRepository;
import com.mozip.domain.user.dto.NotificationSettings;
import com.mozip.domain.user.entity.Role;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.exception.UserErrorCode;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;

import lombok.RequiredArgsConstructor;

// B의 inviteMember(INVITATION), assignReviewers(EVALUATION_REQUEST), 결과 안내(DOC_RESULT, FINAL_RESULT)에서 호출하는 연결 함수
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

	private static final int MAX_PAGE_SIZE = 50;
	// Notification 엔티티/DDL의 컬럼 길이와 맞춤 (db/ddl/001_#48_notification.sql)
	private static final int MAX_TITLE_LENGTH = 200;
	private static final int MAX_LINK_URL_LENGTH = 500;

	private final NotificationRepository notificationRepository;
	private final UserRepository userRepository;
	private final OrganizationRepository organizationRepository;
	private final ApplicationEventPublisher eventPublisher;

	@Transactional
	public void send(Long userId, NotificationType type, Long organizationId, String title, String linkUrl) {
		send(userId, type, organizationId, null, title, linkUrl);
	}

	// 결과 안내처럼 알림이 가리키는 메시지가 있는 경우용 오버로드
	// title/linkUrl/userId/organizationId는 모두 호출자(B)가 만들거나 이미 검사한 값이라, 여기서 어긋나면
	// 호출자의 버그다. 사용자가 고칠 수 있는 입력이 아니므로 BusinessException이 아니라 IllegalArgumentException으로
	// 던진다 (code.md 예외 구분). send가 inviteMember/assignReviewers 트랜잭션 안에서 불리므로 여기서 던지면
	// 호출한 트랜잭션 전체가 롤백된다 — title에 단체명/공고명을 넣는 호출자는 합쳐도 200자를 넘지 않는지 미리 확인해야 함
	@Transactional
	public void send(Long userId, NotificationType type, Long organizationId, Long messageId, String title,
		String linkUrl) {
		if (title != null && title.length() > MAX_TITLE_LENGTH) {
			throw new IllegalArgumentException("title이 %d자를 넘음: %d자".formatted(MAX_TITLE_LENGTH, title.length()));
		}
		if (linkUrl != null && linkUrl.length() > MAX_LINK_URL_LENGTH) {
			throw new IllegalArgumentException(
				"linkUrl이 %d자를 넘음: %d자".formatted(MAX_LINK_URL_LENGTH, linkUrl.length()));
		}
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new IllegalArgumentException("알림 대상 회원을 찾을 수 없음: userId=" + userId));
		if (!type.isAllowedFor(user)) {
			return;
		}
		Organization organization = findOrganization(organizationId);

		Notification notification = notificationRepository.save(
			Notification.create(user, type, organization, messageId, title, linkUrl));

		// organizationId가 null일 때만 organization도 null (존재하지 않는 id면 findOrganization이 먼저 던짐)
		Long broadcastOrganizationId = organization == null ? null : organization.getId();

		// 커밋 전에 롤백되면 알림도 같이 사라져야 하므로, 실제 SSE/Redis 발행은 커밋 후(NotificationEventListener)에서 함
		eventPublisher.publishEvent(new NotificationCreatedEvent(notification.getId(), userId, type, title, linkUrl,
			broadcastOrganizationId, notification.getCreatedAt()));
	}

	// tab이 없으면 지원자/운영진 알림 전체를 최신순으로 반환
	public List<Notification> notifications(Long userId, Role tab, int page, int size) {
		if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT)
				.addContext("page", page)
				.addContext("size", size);
		}
		Pageable pageable = PageRequest.of(page, size);
		return tab == null
			? notificationRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId, pageable)
			: notificationRepository.findAllByUserIdAndTypeInOrderByCreatedAtDescIdDesc(userId,
				NotificationType.byTab(tab), pageable);
	}

	// Notification.unreadCount 필드 배치 조회(컨트롤러)에서 씀. tab이 없으면 전체 개수
	public int unreadCount(Long userId, Role tab) {
		long count = tab == null
			? notificationRepository.countByUserIdAndIsReadFalse(userId)
			: notificationRepository.countByUserIdAndIsReadFalseAndTypeIn(userId, NotificationType.byTab(tab));
		return (int)count;
	}

	@Transactional
	public void markRead(Long userId, List<Long> ids) {
		// 중복 id가 있어도 자기 알림이면 정당한 요청이므로, 개수가 아니라 고유 id 집합으로 소유자 검사를 함
		Set<Long> distinctIds = new HashSet<>(ids);
		List<Notification> notifications = notificationRepository.findAllByIdInAndUserId(distinctIds, userId);
		if (notifications.size() != distinctIds.size()) {
			throw new BusinessException(NotificationErrorCode.FORBIDDEN)
				.addContext("userId", userId)
				.addContext("ids", ids);
		}
		notifications.forEach(Notification::markRead);
	}

	@Transactional
	public NotificationSettings updateSettings(Long userId, boolean notifyResult, boolean notifyScrap,
		boolean notifyOperator) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(UserErrorCode.NOT_FOUND).addContext("userId", userId));
		user.updateNotificationSettings(notifyResult, notifyScrap, notifyOperator);
		return NotificationSettings.from(user);
	}

	// organizationId를 줬는데 찾을 수 없으면 호출한 쪽(B)이 잘못된/오래된 id를 넘긴 버그
	private Organization findOrganization(Long organizationId) {
		if (organizationId == null) {
			return null;
		}
		return organizationRepository.findById(organizationId)
			.orElseThrow(() -> new IllegalArgumentException("알림에 지정된 단체를 찾을 수 없음: organizationId=" + organizationId));
	}

}
