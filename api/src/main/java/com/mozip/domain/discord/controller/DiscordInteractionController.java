package com.mozip.domain.discord.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.mozip.domain.discord.dto.DiscordInteractionResponse;
import com.mozip.domain.discord.service.DiscordInteractionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class DiscordInteractionController implements DiscordInteractionControllerApi {

	public static final String SIGNATURE_HEADER = "X-Signature-Ed25519";
	public static final String TIMESTAMP_HEADER = "X-Signature-Timestamp";

	private final DiscordInteractionService discordInteractionService;

	// 서명 검증에 원문이 필요해 body를 파싱하지 않고 바이트로 받음, 헤더가 없어도 서비스에서 401로 응답
	@Override
	@PostMapping("/api/discord/interactions")
	public DiscordInteractionResponse interactions(
		@RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
		@RequestHeader(value = TIMESTAMP_HEADER, required = false) String timestamp,
		@RequestBody byte[] body) {
		return discordInteractionService.handle(signature, timestamp, body);
	}

}
