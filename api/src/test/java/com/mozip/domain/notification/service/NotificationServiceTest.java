package com.mozip.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.TestcontainersConfiguration;
import com.mozip.domain.notification.entity.Notification;
import com.mozip.domain.notification.entity.NotificationType;
import com.mozip.domain.notification.exception.NotificationErrorCode;
import com.mozip.domain.user.dto.NotificationSettings;
import com.mozip.domain.user.entity.Role;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class NotificationServiceTest {

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private UserRepository userRepository;

	private User user;

	@BeforeEach
	void setUp() {
		user = userRepository.save(
			User.create("01011110001", "encoded", "테스트", false, LocalDateTime.now()));
	}

	@Test
	@DisplayName("알림 설정이 꺼져 있으면 그 설정을 따르는 type은 저장하지 않는다")
	void skipsWhenSettingOff() {
		user.updateNotificationSettings(true, false, true);

		notificationService.send(user.getId(), NotificationType.SCRAP_OPEN, null, "스크랩한 공고가 열렸어요", null);

		List<Notification> notifications = notificationService.notifications(user.getId(), Role.APPLICANT, 0, 10);
		assertThat(notifications).isEmpty();
	}

	@Test
	@DisplayName("설정과 상관없이 항상 보내는 type은 설정이 꺼져 있어도 저장한다")
	void alwaysSendsRegardlessOfSetting() {
		user.updateNotificationSettings(false, false, false);

		notificationService.send(user.getId(), NotificationType.POSTING_NOTICE, null, "공고가 올라왔어요", null);

		List<Notification> notifications = notificationService.notifications(user.getId(), Role.APPLICANT, 0, 10);
		assertThat(notifications).hasSize(1);
	}

	@Test
	@DisplayName("notifications는 role 탭에 해당하는 type만, 최신순으로, 페이지 크기만큼 반환한다")
	void notificationsOrderedByLatestWithPaging() throws InterruptedException {
		notificationService.send(user.getId(), NotificationType.DOC_RESULT, null, "서류 결과 1", null);
		Thread.sleep(5);
		notificationService.send(user.getId(), NotificationType.FINAL_RESULT, null, "서류 결과 2", null);
		Thread.sleep(5);
		notificationService.send(user.getId(), NotificationType.SCRAP_OPEN, null, "서류 결과 3", null);
		// 운영진 탭이라 지원자 목록에는 안 보여야 함
		notificationService.send(user.getId(), NotificationType.EVALUATION_REQUEST, null, "운영진 알림", null);

		List<Notification> page = notificationService.notifications(user.getId(), Role.APPLICANT, 0, 2);

		assertThat(page).hasSize(2);
		assertThat(page.get(0).getTitle()).isEqualTo("서류 결과 3");
		assertThat(page.get(1).getTitle()).isEqualTo("서류 결과 2");
	}

	@Test
	@DisplayName("본인 알림만 읽음 처리할 수 있다")
	void marksOwnNotificationsRead() {
		notificationService.send(user.getId(), NotificationType.DOC_RESULT, null, "서류 결과", null);
		Long notificationId = notificationService.notifications(user.getId(), Role.APPLICANT, 0, 10)
			.get(0)
			.getId();

		notificationService.markRead(user.getId(), List.of(notificationId));

		Notification notification = notificationService.notifications(user.getId(), Role.APPLICANT, 0, 10).get(0);
		assertThat(notification.isRead()).isTrue();
	}

	@Test
	@DisplayName("요청한 id 중 본인 알림이 아닌 게 섞여 있으면 NOTIFICATION_403을 던지고 전부 읽음 처리하지 않는다")
	void rejectsMarkingOthersNotificationRead() {
		User other = userRepository.save(User.create("01011110002", "encoded", "다른회원", false, LocalDateTime.now()));
		notificationService.send(user.getId(), NotificationType.DOC_RESULT, null, "내 알림", null);
		notificationService.send(other.getId(), NotificationType.DOC_RESULT, null, "남의 알림", null);

		Long myId = notificationService.notifications(user.getId(), Role.APPLICANT, 0, 10).get(0).getId();
		Long otherId = notificationService.notifications(other.getId(), Role.APPLICANT, 0, 10).get(0).getId();

		assertThatThrownBy(() -> notificationService.markRead(user.getId(), List.of(myId, otherId)))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(NotificationErrorCode.FORBIDDEN);

		Notification mine = notificationService.notifications(user.getId(), Role.APPLICANT, 0, 10).get(0);
		assertThat(mine.isRead()).isFalse();
	}

	@Test
	@DisplayName("요청한 id에 본인 알림이 중복으로 섞여 있어도 거부하지 않고 읽음 처리한다")
	void allowsDuplicateIdsOfOwnNotification() {
		notificationService.send(user.getId(), NotificationType.DOC_RESULT, null, "내 알림", null);
		Long myId = notificationService.notifications(user.getId(), Role.APPLICANT, 0, 10).get(0).getId();

		notificationService.markRead(user.getId(), List.of(myId, myId));

		Notification notification = notificationService.notifications(user.getId(), Role.APPLICANT, 0, 10).get(0);
		assertThat(notification.isRead()).isTrue();
	}

	@Test
	@DisplayName("page가 음수거나 size가 범위를 벗어나면 COMMON_400을 던진다")
	void rejectsInvalidPageOrSize() {
		assertThatThrownBy(() -> notificationService.notifications(user.getId(), Role.APPLICANT, -1, 10))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(CommonErrorCode.INVALID_INPUT);

		assertThatThrownBy(() -> notificationService.notifications(user.getId(), Role.APPLICANT, 0, 0))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(CommonErrorCode.INVALID_INPUT);

		assertThatThrownBy(() -> notificationService.notifications(user.getId(), Role.APPLICANT, 0, 51))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(CommonErrorCode.INVALID_INPUT);
	}

	@Test
	@DisplayName("존재하지 않는 organizationId를 주면 호출자 버그로 보고 IllegalArgumentException을 던진다")
	void rejectsOrganizationIdNotFound() {
		assertThatThrownBy(
			() -> notificationService.send(user.getId(), NotificationType.DOC_RESULT, 999_999L, "내 알림", null))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("존재하지 않는 userId를 주면 호출자 버그로 보고 IllegalArgumentException을 던진다")
	void rejectsUserIdNotFound() {
		assertThatThrownBy(
			() -> notificationService.send(999_999L, NotificationType.DOC_RESULT, null, "내 알림", null))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("title이 컬럼 길이(200자)를 넘으면 IllegalArgumentException을 던지고 저장하지 않는다")
	void rejectsTooLongTitle() {
		String tooLong = "가".repeat(201);

		assertThatThrownBy(
			() -> notificationService.send(user.getId(), NotificationType.DOC_RESULT, null, tooLong, null))
			.isInstanceOf(IllegalArgumentException.class);

		assertThat(notificationService.notifications(user.getId(), Role.APPLICANT, 0, 10)).isEmpty();
	}

	@Test
	@DisplayName("linkUrl이 컬럼 길이(500자)를 넘으면 IllegalArgumentException을 던진다")
	void rejectsTooLongLinkUrl() {
		String tooLong = "https://mo-zip.com/" + "a".repeat(500);

		assertThatThrownBy(
			() -> notificationService.send(user.getId(), NotificationType.DOC_RESULT, null, "제목", tooLong))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("알림 설정을 변경하면 변경된 값을 반환한다")
	void updatesSettings() {
		NotificationSettings updated = notificationService.updateSettings(user.getId(), false, true, false);

		assertThat(updated.notifyResult()).isFalse();
		assertThat(updated.notifyScrap()).isTrue();
		assertThat(updated.notifyOperator()).isFalse();
	}

}
