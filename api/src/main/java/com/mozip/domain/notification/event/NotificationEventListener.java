package com.mozip.domain.notification.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.mozip.domain.notification.redis.NotificationPublisher;

import lombok.RequiredArgsConstructor;

// send()가 발행한 이벤트를 트랜잭션이 커밋된 뒤에만 Redis로 내보냄. 롤백되면 이 메서드 자체가 호출되지 않음
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

	private final NotificationPublisher notificationPublisher;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onNotificationCreated(NotificationCreatedEvent event) {
		notificationPublisher.publish(event);
	}

}
