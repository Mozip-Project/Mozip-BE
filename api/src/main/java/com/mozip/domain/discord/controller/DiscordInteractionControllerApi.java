package com.mozip.domain.discord.controller;

import com.mozip.domain.discord.dto.DiscordInteractionResponse;
import com.mozip.global.swagger.ApiErrorCodes;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

// Swagger 문서용 어노테이션은 Controller 대신 ControllerApi 인터페이스에 작성
@Tag(name = "Discord", description = "디스코드가 호출하는 콜백 (프론트에서 호출하지 않음)")
public interface DiscordInteractionControllerApi {

	@Operation(summary = "디스코드 Interactions 콜백", description = """
		운영팀 디스코드에서 단체 등록 심사 버튼(승인, 미승인)을 누르면 디스코드가 호출합니다.
		로그인 대신 `X-Signature-Ed25519`, `X-Signature-Timestamp` 헤더의 Ed25519 서명으로 검증하며 실패하면 `DISCORD_401`입니다.
		응답은 `ApiResponse`로 감싸지 않고 디스코드 Interaction Response 형식 그대로입니다.""")
	@SecurityRequirements
	@ApiErrorCodes({"COMMON_400", "DISCORD_401"})
	DiscordInteractionResponse interactions(String signature, String timestamp, byte[] body);

}
