package com.mozip.domain.message.dto;

import java.util.List;

import com.mozip.domain.message.entity.Message;
import com.mozip.domain.message.entity.MessageTarget;

// 결과 안내 화면의 탭 하나. message가 null이면 아직 초안을 저장하지 않은 탭
public record ResultMessagePanel(MessageTarget target, Message message, MessageTemplate template,
	int recipientCount, int sendableCount, int processedCount, int deliveredCount, int failedCount,
	List<Recipient> recipients) {
}
