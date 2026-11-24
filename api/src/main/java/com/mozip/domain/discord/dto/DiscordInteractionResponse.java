package com.mozip.domain.discord.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

// 디스코드 Interactions 콜백 응답 (ApiResponse로 감싸지 않고 디스코드가 정한 형식 그대로)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DiscordInteractionResponse(
	int type,
	Map<String, Object> data
) {

	private static final int TYPE_PONG = 1;
	private static final int TYPE_CHANNEL_MESSAGE = 4;
	private static final int TYPE_UPDATE_MESSAGE = 7;
	private static final int TYPE_MODAL = 9;
	// 누른 사람에게만 보이는 메시지
	private static final int FLAG_EPHEMERAL = 64;
	private static final int COMPONENT_ACTION_ROW = 1;
	private static final int COMPONENT_TEXT_INPUT = 4;
	private static final int TEXT_INPUT_PARAGRAPH = 2;
	// 단체 이름 등에 섞인 @everyone, 역할 멘션이 실제 알림으로 가지 않게 함
	private static final Map<String, Object> NO_MENTIONS = Map.of("parse", List.of());

	public static DiscordInteractionResponse pong() {
		return new DiscordInteractionResponse(TYPE_PONG, null);
	}

	public static DiscordInteractionResponse ephemeral(String content) {
		return new DiscordInteractionResponse(TYPE_CHANNEL_MESSAGE,
			Map.of("content", content, "flags", FLAG_EPHEMERAL, "allowed_mentions", NO_MENTIONS));
	}

	// 버튼이 붙은 원래 메시지의 본문을 바꾸고 버튼을 제거 (embeds는 보내지 않아 그대로 남음)
	public static DiscordInteractionResponse updateMessage(String content) {
		return new DiscordInteractionResponse(TYPE_UPDATE_MESSAGE,
			Map.of("content", content, "components", List.of(), "allowed_mentions", NO_MENTIONS));
	}

	// 여러 줄 입력칸 하나짜리 모달
	public static DiscordInteractionResponse modal(String customId, String title, String inputCustomId,
		String inputLabel, int maxLength) {
		Map<String, Object> textInput = Map.of(
			"type", COMPONENT_TEXT_INPUT,
			"custom_id", inputCustomId,
			"label", inputLabel,
			"style", TEXT_INPUT_PARAGRAPH,
			"min_length", 1,
			"max_length", maxLength,
			"required", true);
		return new DiscordInteractionResponse(TYPE_MODAL, Map.of(
			"custom_id", customId,
			"title", title,
			"components", List.of(Map.of("type", COMPONENT_ACTION_ROW, "components", List.of(textInput)))));
	}

}
