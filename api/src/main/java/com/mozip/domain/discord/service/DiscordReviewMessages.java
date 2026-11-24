package com.mozip.domain.discord.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mozip.domain.organization.entity.CouncilLevel;
import com.mozip.domain.organization.entity.OrganizationScope;
import com.mozip.domain.organization.entity.OrganizationType;
import com.mozip.domain.organization.entity.ReviewStatus;
import com.mozip.domain.organization.service.OrganizationCreatedEvent;

// 단체 등록 심사 메시지 본문과 버튼 custom_id 형식 (보내는 쪽과 Interactions 처리 쪽이 같이 씀)
public final class DiscordReviewMessages {

	public static final String ACTION_APPROVE = "approve";
	public static final String ACTION_REJECT = "reject";
	public static final String ACTION_REJECT_MODAL = "reject-modal";
	public static final String REASON_INPUT_ID = "reason";

	private static final String CUSTOM_ID_PREFIX = "org-review";
	private static final int COMPONENT_ACTION_ROW = 1;
	private static final int COMPONENT_BUTTON = 2;
	private static final int BUTTON_STYLE_SUCCESS = 3;
	private static final int BUTTON_STYLE_DANGER = 4;
	private static final String EMPTY = "없음";

	private DiscordReviewMessages() {
	}

	// custom_id는 "org-review:동작:단체id"
	public static String customId(String action, Long organizationId) {
		return CUSTOM_ID_PREFIX + ":" + action + ":" + organizationId;
	}

	// 우리 형식이 아닌 custom_id면 빈 값
	public static Optional<ReviewAction> parseCustomId(String customId) {
		if (customId == null) {
			return Optional.empty();
		}
		String[] parts = customId.split(":");
		if (parts.length != 3 || !CUSTOM_ID_PREFIX.equals(parts[0])) {
			return Optional.empty();
		}
		try {
			return Optional.of(new ReviewAction(parts[1], Long.parseLong(parts[2])));
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}

	// 심사 요청 메시지 (단체 정보 embed + 승인/미승인 버튼)
	public static Map<String, Object> reviewRequest(OrganizationCreatedEvent event) {
		List<Map<String, Object>> fields = new ArrayList<>();
		fields.add(field("단체 이름", event.name(), false));
		fields.add(field("유형", typeLabel(event.type(), event.councilLevel()), true));
		fields.add(field("범위", scopeLabel(event.scope()), true));
		fields.add(field("학교", event.schoolName(), true));
		fields.add(field("대표자", event.representativeName() + " (회원 id " + event.representativeUserId() + ")", false));
		fields.add(field("증빙 링크", event.proofLink(), false));
		fields.add(field("증빙 파일", event.proofFileUrl(), false));

		Map<String, Object> embed = Map.of(
			"title", "단체 id " + event.organizationId(),
			"fields", fields);
		Map<String, Object> buttons = Map.of(
			"type", COMPONENT_ACTION_ROW,
			"components", List.of(
				button("승인", BUTTON_STYLE_SUCCESS, customId(ACTION_APPROVE, event.organizationId())),
				button("미승인", BUTTON_STYLE_DANGER, customId(ACTION_REJECT, event.organizationId()))));
		return Map.of(
			"content", "**단체 등록 심사 요청**",
			"embeds", List.of(embed),
			"components", List.of(buttons),
			// 단체 이름 등에 섞인 @everyone, 역할 멘션이 실제 알림으로 가지 않게 함
			"allowed_mentions", Map.of("parse", List.of()));
	}

	// 처리 후 원래 메시지 본문을 대체할 결과 (시각은 보는 사람 시간대로 표시되는 디스코드 타임스탬프)
	public static String reviewResult(ReviewStatus status, String reviewerUserId, Instant reviewedAt,
		String rejectReason) {
		StringBuilder content = new StringBuilder()
			.append("**단체 등록 심사 완료: ").append(statusLabel(status)).append("**\n")
			.append("처리자: <@").append(reviewerUserId).append(">\n")
			.append("처리 시각: <t:").append(reviewedAt.getEpochSecond()).append(":f>");
		if (rejectReason != null) {
			content.append("\n사유: ").append(rejectReason);
		}
		return content.toString();
	}

	public static String statusLabel(ReviewStatus status) {
		return switch (status) {
			case PENDING -> "심사 대기";
			case APPROVED -> "승인";
			case REJECTED -> "미승인";
		};
	}

	private static String typeLabel(OrganizationType type, CouncilLevel councilLevel) {
		String label = switch (type) {
			case CLUB -> "동아리";
			case ACADEMIC -> "학회";
			case STUDENT_COUNCIL -> "학생회";
		};
		if (councilLevel == null) {
			return label;
		}
		return label + " (" + switch (councilLevel) {
			case UNIVERSITY -> "총학생회";
			case COLLEGE -> "단과대";
			case DEPARTMENT -> "학과";
		} + ")";
	}

	private static String scopeLabel(OrganizationScope scope) {
		return switch (scope) {
			case CAMPUS -> "교내";
			case UNION -> "연합";
		};
	}

	// embed 필드 값은 비울 수 없어 없으면 "없음"
	private static Map<String, Object> field(String name, String value, boolean inline) {
		return Map.of("name", name, "value", value == null || value.isBlank() ? EMPTY : value, "inline", inline);
	}

	private static Map<String, Object> button(String label, int style, String customId) {
		return Map.of("type", COMPONENT_BUTTON, "style", style, "label", label, "custom_id", customId);
	}

	public record ReviewAction(String action, Long organizationId) {
	}

}
