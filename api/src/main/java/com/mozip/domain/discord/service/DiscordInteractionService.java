package com.mozip.domain.discord.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.mozip.domain.discord.config.DiscordProperties;
import com.mozip.domain.discord.dto.DiscordInteractionResponse;
import com.mozip.domain.discord.exception.DiscordErrorCode;
import com.mozip.domain.discord.service.DiscordReviewMessages.ReviewAction;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.ReviewStatus;
import com.mozip.domain.organization.exception.OrganizationErrorCode;
import com.mozip.domain.organization.service.OrganizationReviewService;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// 디스코드 Interactions 콜백 처리 (서명 검증, PING, 단체 심사 버튼과 미승인 사유 모달)
// 디스코드는 3초 안에 응답을 받아야 하는데 처리가 DB 트랜잭션 하나라 deferred 없이 응답 body로 바로 메시지를 갱신
// 클래스에 트랜잭션을 걸지 않음 (읽기 전용 트랜잭션에 OrganizationReviewService의 쓰기가 합류하지 않게)
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordInteractionService {

	private static final int TYPE_PING = 1;
	private static final int TYPE_MESSAGE_COMPONENT = 3;
	private static final int TYPE_MODAL_SUBMIT = 5;

	private final DiscordSignatureVerifier signatureVerifier;
	private final DiscordProperties properties;
	private final OrganizationReviewService reviewService;
	private final ObjectMapper objectMapper;

	// 서명은 파싱 전 body 원문 바이트로 검증, 실패하면 DISCORD_401
	public DiscordInteractionResponse handle(String signature, String timestamp, byte[] body) {
		if (!signatureVerifier.isValid(signature, timestamp, body)) {
			throw new BusinessException(DiscordErrorCode.INVALID_SIGNATURE);
		}
		JsonNode interaction = parse(body);
		return switch (interaction.path("type").asInt(0)) {
			case TYPE_PING -> DiscordInteractionResponse.pong();
			case TYPE_MESSAGE_COMPONENT -> handleButton(interaction);
			case TYPE_MODAL_SUBMIT -> handleModalSubmit(interaction);
			default -> DiscordInteractionResponse.ephemeral("지원하지 않는 요청입니다.");
		};
	}

	private DiscordInteractionResponse handleButton(JsonNode interaction) {
		Optional<ReviewAction> parsed = parseAction(interaction);
		if (parsed.isEmpty()) {
			return DiscordInteractionResponse.ephemeral("알 수 없는 버튼입니다.");
		}
		ReviewAction action = parsed.get();
		String reviewerId = userId(interaction);
		if (!isReviewer(interaction, reviewerId)) {
			return forbidden(action, reviewerId);
		}
		try {
			return switch (action.action()) {
				case DiscordReviewMessages.ACTION_APPROVE -> approve(action.organizationId(), reviewerId);
				case DiscordReviewMessages.ACTION_REJECT -> openRejectModal(action.organizationId());
				default -> DiscordInteractionResponse.ephemeral("알 수 없는 버튼입니다.");
			};
		} catch (BusinessException e) {
			return reviewFailure(action.organizationId(), e);
		}
	}

	private DiscordInteractionResponse handleModalSubmit(JsonNode interaction) {
		Optional<ReviewAction> parsed = parseAction(interaction);
		if (parsed.isEmpty() || !DiscordReviewMessages.ACTION_REJECT_MODAL.equals(parsed.get().action())) {
			return DiscordInteractionResponse.ephemeral("알 수 없는 요청입니다.");
		}
		ReviewAction action = parsed.get();
		String reviewerId = userId(interaction);
		// 모달이 떠 있는 사이 권한이 빠졌을 수 있어 제출 때도 다시 검사
		if (!isReviewer(interaction, reviewerId)) {
			return forbidden(action, reviewerId);
		}
		String reason = modalValue(interaction, DiscordReviewMessages.REASON_INPUT_ID).strip();
		if (reason.isEmpty() || reason.length() > Organization.REJECT_REASON_MAX_LENGTH) {
			return DiscordInteractionResponse.ephemeral(
				"미승인 사유는 1자 이상 " + Organization.REJECT_REASON_MAX_LENGTH + "자 이하로 입력해 주세요.");
		}
		try {
			reviewService.reject(action.organizationId(), reason);
		} catch (BusinessException e) {
			return reviewFailure(action.organizationId(), e);
		}
		log.info("단체 미승인 처리: organizationId={}, discordUserId={}", action.organizationId(), reviewerId);
		return DiscordInteractionResponse.updateMessage(
			DiscordReviewMessages.reviewResult(ReviewStatus.REJECTED, reviewerId, Instant.now(), reason));
	}

	private DiscordInteractionResponse approve(Long organizationId, String reviewerId) {
		reviewService.approve(organizationId);
		log.info("단체 승인 처리: organizationId={}, discordUserId={}", organizationId, reviewerId);
		return DiscordInteractionResponse.updateMessage(
			DiscordReviewMessages.reviewResult(ReviewStatus.APPROVED, reviewerId, Instant.now(), null));
	}

	// 이미 처리된 단체면 사유를 받기 전에 알림 (제출 시점에 OrganizationReviewService가 잠금 후 다시 확인)
	private DiscordInteractionResponse openRejectModal(Long organizationId) {
		ReviewStatus status = reviewService.getReviewStatus(organizationId);
		if (status != ReviewStatus.PENDING) {
			return alreadyReviewed(status);
		}
		return DiscordInteractionResponse.modal(
			DiscordReviewMessages.customId(DiscordReviewMessages.ACTION_REJECT_MODAL, organizationId),
			"단체 등록 미승인", DiscordReviewMessages.REASON_INPUT_ID, "미승인 사유 (대표자에게 전달됩니다)",
			Organization.REJECT_REASON_MAX_LENGTH);
	}

	// 심사 실패는 HTTP 에러 대신 누른 사람에게만 보이는 메시지로 응답 (상태는 바뀌지 않음)
	private DiscordInteractionResponse reviewFailure(Long organizationId, BusinessException e) {
		if (e.getErrorCode() == OrganizationErrorCode.ORGANIZATION_NOT_FOUND) {
			return DiscordInteractionResponse.ephemeral("단체를 찾을 수 없습니다. (단체 id " + organizationId + ")");
		}
		if (e.getErrorCode() == OrganizationErrorCode.CONFLICT) {
			return alreadyReviewed(reviewService.getReviewStatus(organizationId));
		}
		throw e;
	}

	private static DiscordInteractionResponse alreadyReviewed(ReviewStatus status) {
		return DiscordInteractionResponse.ephemeral(
			"이미 처리된 단체입니다. 현재 상태: " + DiscordReviewMessages.statusLabel(status));
	}

	private static DiscordInteractionResponse forbidden(ReviewAction action, String reviewerId) {
		log.warn("권한 없는 디스코드 사용자의 단체 심사 시도: organizationId={}, discordUserId={}", action.organizationId(),
			reviewerId);
		return DiscordInteractionResponse.ephemeral("단체 심사 권한이 없습니다.");
	}

	private boolean isReviewer(JsonNode interaction, String userId) {
		List<String> roleIds = new ArrayList<>();
		interaction.path("member").path("roles").forEach(role -> roleIds.add(role.asString("")));
		return properties.isReviewer(userId, roleIds);
	}

	// 서버 채널에서는 member.user, DM에서는 user에 누른 사람이 들어옴
	private static String userId(JsonNode interaction) {
		String memberUserId = interaction.path("member").path("user").path("id").asString(null);
		return memberUserId != null ? memberUserId : interaction.path("user").path("id").asString(null);
	}

	private static Optional<ReviewAction> parseAction(JsonNode interaction) {
		return DiscordReviewMessages.parseCustomId(interaction.path("data").path("custom_id").asString(null));
	}

	// 모달 제출 값은 data.components[행].components[입력칸]에 들어옴, 없으면 빈 문자열
	private static String modalValue(JsonNode interaction, String inputCustomId) {
		for (JsonNode row : interaction.path("data").path("components")) {
			for (JsonNode input : row.path("components")) {
				if (inputCustomId.equals(input.path("custom_id").asString(null))) {
					return input.path("value").asString("");
				}
			}
		}
		return "";
	}

	private JsonNode parse(byte[] body) {
		try {
			return objectMapper.readTree(body);
		} catch (JacksonException e) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT);
		}
	}

}
