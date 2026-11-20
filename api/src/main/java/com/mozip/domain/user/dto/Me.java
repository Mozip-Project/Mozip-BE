package com.mozip.domain.user.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.school.entity.School;
import com.mozip.domain.user.entity.Authority;
import com.mozip.domain.user.entity.Role;
import com.mozip.domain.user.entity.UserStatus;

// GraphQL Query.me, Mutation.switchRole이 반환하는 내 정보. User 타입과 별개로 두는 이유는
// 소속 단체 목록, 알림 요약처럼 로그인한 본인에게만 필요한 필드를 섞지 않기 위함
public record Me(
	Long id,
	String phone,
	String name,
	Authority authority,
	UserStatus status,
	Role lastRole,
	Organization lastOrganization,
	School school,
	boolean marketingAgreed,
	LocalDateTime termsAgreedAt,
	// 운영진으로 소속된 단체 목록 (단체 전환 선택 UI에 사용)
	List<Organization> organizations,
	NotificationSettings notificationSettings,
	int unreadNotificationCount,
	LocalDateTime createdAt
) {
}
