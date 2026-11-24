package com.mozip.domain.discord.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.mozip.domain.discord.config.DiscordProperties;
import com.mozip.domain.organization.service.OrganizationCreatedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 단체 생성이 커밋된 뒤 운영팀 디스코드 채널에 심사 요청을 보냄
@Slf4j
@Component
@RequiredArgsConstructor
public class OrganizationReviewRequestListener {

	private final DiscordClient discordClient;
	private final DiscordProperties properties;

	// 이미 커밋된 뒤라 롤백되지는 않지만, 예외가 단체 생성 응답까지 올라가지 않게 여기서 잡고 로그만 남김
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onOrganizationCreated(OrganizationCreatedEvent event) {
		try {
			discordClient.createMessage(properties.reviewChannelId(), DiscordReviewMessages.reviewRequest(event));
		} catch (RuntimeException e) {
			log.error("단체 심사 요청 디스코드 발송 실패: organizationId={}", event.organizationId(), e);
		}
	}

}
