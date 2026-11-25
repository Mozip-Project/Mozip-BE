package com.mozip.domain.discord.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.HexFormat;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.mozip.domain.discord.config.DiscordProperties;

import lombok.extern.slf4j.Slf4j;

// 디스코드 Interactions 요청의 Ed25519 서명 검증 (서명 대상은 timestamp 헤더 값 + 요청 body 원문 바이트)
@Slf4j
@Component
public class DiscordSignatureVerifier {

	// Ed25519 공개키 32바이트 앞에 붙이면 X.509 형식이 되는 고정 접두 바이트
	private static final byte[] X509_PREFIX = HexFormat.of().parseHex("302a300506032b6570032100");
	private static final int RAW_KEY_LENGTH = 32;

	// 공개키 설정이 없거나 잘못되면 null, 이때는 모든 요청을 거부
	private final PublicKey publicKey;

	public DiscordSignatureVerifier(DiscordProperties properties) {
		this.publicKey = parsePublicKey(properties.publicKey());
	}

	public boolean isValid(String signatureHex, String timestamp, byte[] body) {
		if (publicKey == null || !StringUtils.hasText(signatureHex) || !StringUtils.hasText(timestamp)
			|| body == null) {
			return false;
		}
		try {
			Signature signature = Signature.getInstance("Ed25519");
			signature.initVerify(publicKey);
			signature.update(timestamp.getBytes(StandardCharsets.UTF_8));
			signature.update(body);
			return signature.verify(HexFormat.of().parseHex(signatureHex.trim()));
		} catch (GeneralSecurityException | IllegalArgumentException e) {
			// hex가 아닌 서명, 길이가 다른 서명
			return false;
		}
	}

	private static PublicKey parsePublicKey(String publicKeyHex) {
		if (!StringUtils.hasText(publicKeyHex)) {
			return null;
		}
		try {
			byte[] raw = HexFormat.of().parseHex(publicKeyHex.trim());
			if (raw.length != RAW_KEY_LENGTH) {
				throw new IllegalArgumentException("공개키 길이가 32바이트가 아님: " + raw.length);
			}
			byte[] encoded = new byte[X509_PREFIX.length + raw.length];
			System.arraycopy(X509_PREFIX, 0, encoded, 0, X509_PREFIX.length);
			System.arraycopy(raw, 0, encoded, X509_PREFIX.length, raw.length);
			return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
		} catch (GeneralSecurityException | IllegalArgumentException e) {
			log.error("discord.public-key를 읽을 수 없어 디스코드 Interactions 요청을 모두 거부함", e);
			return null;
		}
	}

}
