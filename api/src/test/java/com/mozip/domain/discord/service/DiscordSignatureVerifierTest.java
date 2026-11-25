package com.mozip.domain.discord.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mozip.domain.discord.DiscordTestKeys;
import com.mozip.domain.discord.config.DiscordProperties;

// Ed25519 서명 검증 (Spring 없이)
class DiscordSignatureVerifierTest {

	private static final String TIMESTAMP = "1732412345";

	private final DiscordTestKeys keys = new DiscordTestKeys();
	private final DiscordSignatureVerifier verifier = verifierOf(keys.publicKeyHex());
	private final byte[] body = "{\"type\":1,\"note\":\"한글 본문\"}".getBytes(StandardCharsets.UTF_8);

	@Test
	@DisplayName("timestamp와 body 원문에 대한 서명이 맞으면 통과한다")
	void validSignature() {
		assertThat(verifier.isValid(keys.sign(TIMESTAMP, body), TIMESTAMP, body)).isTrue();
	}

	@Test
	@DisplayName("body나 timestamp가 서명한 것과 다르면 거부한다")
	void tampered() {
		String signature = keys.sign(TIMESTAMP, body);
		byte[] otherBody = "{\"type\":3}".getBytes(StandardCharsets.UTF_8);

		assertThat(verifier.isValid(signature, TIMESTAMP, otherBody)).isFalse();
		assertThat(verifier.isValid(signature, "1732412346", body)).isFalse();
	}

	@Test
	@DisplayName("다른 키로 만든 서명은 거부한다")
	void otherKey() {
		String signature = new DiscordTestKeys().sign(TIMESTAMP, body);

		assertThat(verifier.isValid(signature, TIMESTAMP, body)).isFalse();
	}

	@Test
	@DisplayName("서명이나 timestamp 헤더가 없거나 서명이 hex가 아니면 거부한다")
	void malformed() {
		String signature = keys.sign(TIMESTAMP, body);

		assertThat(verifier.isValid(null, TIMESTAMP, body)).isFalse();
		assertThat(verifier.isValid(signature, null, body)).isFalse();
		assertThat(verifier.isValid("", TIMESTAMP, body)).isFalse();
		assertThat(verifier.isValid("서명아님", TIMESTAMP, body)).isFalse();
		assertThat(verifier.isValid("abcd", TIMESTAMP, body)).isFalse();
	}

	@Test
	@DisplayName("공개키 설정이 비었거나 형식이 틀리면 올바른 서명도 모두 거부한다")
	void publicKeyMissing() {
		String signature = keys.sign(TIMESTAMP, body);

		assertThat(verifierOf(null).isValid(signature, TIMESTAMP, body)).isFalse();
		assertThat(verifierOf("").isValid(signature, TIMESTAMP, body)).isFalse();
		assertThat(verifierOf("abcd").isValid(signature, TIMESTAMP, body)).isFalse();
		assertThat(verifierOf("hex가 아닌 값").isValid(signature, TIMESTAMP, body)).isFalse();
	}

	private static DiscordSignatureVerifier verifierOf(String publicKeyHex) {
		return new DiscordSignatureVerifier(
			new DiscordProperties(false, null, publicKeyHex, null, List.of(), List.of(), "https://discord.test"));
	}

}
