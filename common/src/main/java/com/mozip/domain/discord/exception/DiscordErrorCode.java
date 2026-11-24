package com.mozip.domain.discord.exception;

import org.springframework.http.HttpStatus;

import com.mozip.global.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DiscordErrorCode implements ErrorCode {

	// Interactions 요청의 서명 헤더가 없거나 검증 실패, 디스코드가 URL 등록 때 401을 기대함
	INVALID_SIGNATURE(HttpStatus.UNAUTHORIZED, "DISCORD_401", "디스코드 요청 서명이 올바르지 않습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;

}
