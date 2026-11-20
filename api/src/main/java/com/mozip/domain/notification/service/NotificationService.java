package com.mozip.domain.notification.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.domain.notification.entity.Notification;
import com.mozip.domain.notification.entity.NotificationType;
import com.mozip.domain.notification.event.NotificationCreatedEvent;
import com.mozip.domain.notification.exception.NotificationErrorCode;
import com.mozip.domain.notification.repository.NotificationRepository;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.repository.OrganizationRepository;
import com.mozip.domain.user.entity.Role;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.exception.UserErrorCode;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// B의 inviteMember(INVITATION), assignReviewers(EVALUATION_REQUEST), 결과 안내(DOC_RESULT, FINAL_RESULT)에서 호출하는 연결 함수
@Slf4j
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
	@Transactional
	public void send(Long userId, NotificationType type, Long organizationId, Long messageId, String title,
		String linkUrl) {
		if (title != null && title.length() > MAX_TITLE_LENGTH) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT)
				.addContext("titleLength", title.length());
		}
		if (linkUrl != null && linkUrl.length() > MAX_LINK_URL_LENGTH) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT)
				.addContext("linkUrlLength", linkUrl.length());
		}
		User user = userRepository.findById(userId).orElse(null);
		if (user == null) {
			// 호출자(B)가 이미 유효한 userId로 호출하는 내부 연결 함수라 예외 대신 로그로 남김
			log.warn("알림 대상 회원을 찾을 수 없어 건너뜀: userId={}, type={}", userId, type);
			return;
		}
		if (!type.isAllowedFor(user)) {
			return;
		}
		Organization organization = findOrganizationOrWarn(organizationId, userId, type);

		Notification notification = notificationRepository.save(
			Notification.create(user, type, organization, messageId, title, linkUrl));

		// organization이 아니라 원본 organizationId를 그대로 넘기면, DB엔 단체 없이 저장됐는데(findOrganizationOrWarn이
		// null로 바꿈) 이벤트엔 존재하지 않는 organizationId가 실려 나가는 불일치가 생김
		Long broadcastOrganizationId = organization == null ? null : organization.getId();

		// 커밋 전에 롤백되면 알림도 같이 사라져야 하므로, 실제 SSE/Redis 발행은 커밋 후(NotificationEventListener)에서 함
		eventPublisher.publishEvent(new NotificationCreatedEvent(notification.getId(), userId, type, title, linkUrl,
			broadcastOrganizationId, notification.getCreatedAt()));
	}

	public List<Notification> notifications(Long userId, Role tab, int page, int size) {
		if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT)
				.addContext("page", page)
				.addContext("size", size);
		}
		return notificationRepository.findAllByUserIdAndTypeInOrderByCreatedAtDescIdDesc(userId,
			NotificationType.byTab(tab), PageRequest.of(page, size));
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
	public User updateSettings(Long userId, boolean notifyResult, boolean notifyScrap, boolean notifyOperator) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(UserErrorCode.NOT_FOUND).addContext("userId", userId));
		user.updateNotificationSettings(notifyResult, notifyScrap, notifyOperator);
		return user;
	}

	// organizationId를 줬는데 찾을 수 없으면 호출한 쪽(B)이 잘못된/오래된 id를 넘겼을 가능성이 있어 로그로 남김
	private Organization findOrganizationOrWarn(Long organizationId, Long userId, NotificationType type) {
		if (organizationId == null) {
			return null;
		}
		Organization organization = organizationRepository.findById(organizationId).orElse(null);
		if (organization == null) {
			log.warn("알림에 지정된 단체를 찾을 수 없어 단체 없이 저장함: organizationId={}, userId={}, type={}",
				organizationId, userId, type);
		}
		return organization;
	}

}
