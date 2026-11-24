package com.mozip.domain.discord;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Arrays;
import java.util.HexFormat;

// 테스트용 Ed25519 키 쌍. 디스코드가 하는 것과 같은 방식(timestamp + body)으로 서명을 만듦
public class DiscordTestKeys {

	private static final int RAW_KEY_LENGTH = 32;

	private final KeyPair keyPair;

	public DiscordTestKeys() {
		try {
			this.keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	// 디스코드 개발자 콘솔의 Public Key와 같은 형식 (X.509 인코딩의 마지막 32바이트를 hex로)
	public String publicKeyHex() {
		byte[] encoded = keyPair.getPublic().getEncoded();
		return HexFormat.of().formatHex(Arrays.copyOfRange(encoded, encoded.length - RAW_KEY_LENGTH, encoded.length));
	}

	public String sign(String timestamp, byte[] body) {
		try {
			Signature signature = Signature.getInstance("Ed25519");
			signature.initSign(keyPair.getPrivate());
			signature.update(timestamp.getBytes(StandardCharsets.UTF_8));
			signature.update(body);
			return HexFormat.of().formatHex(signature.sign());
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

}
