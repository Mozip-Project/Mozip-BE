package com.mozip.domain.notification.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import com.mozip.domain.notification.dto.UpdateNotificationSettingsInput;
import com.mozip.domain.notification.entity.Notification;
import com.mozip.domain.notification.service.NotificationService;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.repository.OrganizationRepository;
import com.mozip.domain.user.dto.NotificationSettings;
import com.mozip.domain.user.entity.Role;
import com.mozip.global.security.AuthUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class NotificationGraphqlController {

	private final NotificationService notificationService;
	private final OrganizationRepository organizationRepository;

	@QueryMapping
	public List<Notification> notifications(@Argument Role role, @Argument int page, @Argument int size,
		@AuthenticationPrincipal AuthUser authUser) {
		return notificationService.notifications(authUser.userId(), role, page, size);
	}

	@MutationMapping
	public boolean markNotificationsRead(@Argument List<Long> ids, @AuthenticationPrincipal AuthUser authUser) {
		notificationService.markRead(authUser.userId(), ids);
		return true;
	}

	@MutationMapping
	public NotificationSettings updateNotificationSettings(@Argument @Valid UpdateNotificationSettingsInput input,
		@AuthenticationPrincipal AuthUser authUser) {
		return notificationService.updateSettings(authUser.userId(), input.notifyResult(), input.notifyScrap(),
			input.notifyOperator());
	}

	// 각 알림이 속한 탭(지원자/운영진)의 읽지 않은 개수. role 없이 조회해 두 탭이 섞여도 항목마다 자기 탭 기준으로 셈
	// (userId, tab) 조합별로 한 번만 세어 같은 탭 항목끼리 캐시 공유 (N+1 방지)
	@BatchMapping(typeName = "Notification", field = "unreadCount")
	public Map<Notification, Integer> unreadCount(List<Notification> notifications) {
		Map<List<Object>, Integer> countsByUserAndTab = new HashMap<>();
		return notifications.stream()
			.collect(Collectors.toMap(Function.identity(), notification -> {
				Long userId = notification.getUser().getId();
				Role tab = notification.getType().tab();
				return countsByUserAndTab.computeIfAbsent(List.of(userId, tab),
					key -> notificationService.unreadCount(userId, tab));
			}));
	}

	// 알림 목록을 조회할 때 각 알림의 organization을 한 번에 로딩 (N+1 방지)
	// 조회된 항목의 organization은 프록시일 수 있기 때문에 id로 조회하여 결과 Map의 키는 GraphQL이 넘긴 부모 객체 그대로 사용
	@BatchMapping(typeName = "Notification", field = "organization")
	public Map<Notification, Organization> organization(List<Notification> notifications) {
		List<Long> organizationIds = notifications.stream()
			.map(Notification::getOrganization)
			.filter(Objects::nonNull)
			.map(Organization::getId)
			.distinct()
			.toList();

		Map<Long, Organization> organizationsById = organizationRepository.findAllById(organizationIds).stream()
			.collect(Collectors.toMap(Organization::getId, Function.identity()));

		return notifications.stream()
			.filter(notification -> notification.getOrganization() != null)
			.collect(Collectors.toMap(Function.identity(),
				notification -> organizationsById.get(notification.getOrganization().getId())));
	}

}
