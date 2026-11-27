package com.mozip.domain.notification.event;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.mozip.TestcontainersConfiguration;
import com.mozip.domain.notification.entity.NotificationType;
import com.mozip.domain.notification.redis.NotificationPublisher;
import com.mozip.domain.notification.service.NotificationService;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;

// send()가 커밋된 뒤에만 Redis로 발행되는지 확인. 테스트 메서드 자체는 @Transactional을 안 붙여서
// TransactionTemplate으로 커밋/롤백을 직접 제어함 (@Transactional 테스트는 항상 롤백돼 AFTER_COMMIT이 안 불림)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class NotificationEventListenerTest {

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@MockitoSpyBean
	private NotificationPublisher notificationPublisher;

	private Long userId;

	// 실제로 커밋되는 테스트라 데이터가 남으므로 phone은 매번 다르게 지어 충돌을 피함 (롤백 테스트와 공유 DB)
	@BeforeEach
	void setUp() {
		String phone = "010" + ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999);
		userId = userRepository.save(User.create(phone, "encoded", "테스트", false, LocalDateTime.now())).getId();
	}

	@Test
	@DisplayName("트랜잭션이 커밋되면 Redis로 발행한다")
	void publishesAfterCommit() {
		new TransactionTemplate(transactionManager).executeWithoutResult(status ->
			notificationService.send(userId, NotificationType.POSTING_NOTICE, null, "공고 공지", null));

		verify(notificationPublisher).publish(any());
	}

	@Test
	@DisplayName("트랜잭션이 롤백되면 Redis로 발행하지 않는다")
	void doesNotPublishOnRollback() {
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			notificationService.send(userId, NotificationType.POSTING_NOTICE, null, "공고 공지", null);
			status.setRollbackOnly();
		});

		verify(notificationPublisher, never()).publish(any());
	}

	@Test
	@DisplayName("존재하지 않는 organizationId로 보내면 예외를 던지고 발행하지 않는다")
	void doesNotPublishWhenOrganizationIdNotFound() {
		assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status ->
			notificationService.send(userId, NotificationType.DOC_RESULT, 999_999L, "서류 결과", null)))
			.isInstanceOf(IllegalArgumentException.class);

		verify(notificationPublisher, never()).publish(any());
	}

}
