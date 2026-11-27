package com.mozip.domain.notification.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.mozip.domain.notification.entity.Notification;
import com.mozip.domain.notification.entity.NotificationType;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	// 알림 목록. 지원자/운영진 탭에 해당하는 type만 최신순으로
	List<Notification> findAllByUserIdAndTypeInOrderByCreatedAtDescIdDesc(Long userId, Collection<NotificationType> types,
		Pageable pageable);

	// role 없이 전체 조회. 탭 구분 없이 내 알림을 전부 최신순으로
	List<Notification> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

	// 읽지 않은 전체 개수
	long countByUserIdAndIsReadFalse(Long userId);

	// 읽지 않은 탭별 개수
	long countByUserIdAndIsReadFalseAndTypeIn(Long userId, Collection<NotificationType> types);

	// 읽음 처리 전 소유자 검사용 (요청한 id 중 실제로 그 회원 것만 걸러짐)
	List<Notification> findAllByIdInAndUserId(Collection<Long> ids, Long userId);

}
