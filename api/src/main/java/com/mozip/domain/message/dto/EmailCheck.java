package com.mozip.domain.message.dto;

// 이메일 경고 판정 결과. warning이 null이면 정상, suggestedDomain은 SUSPICIOUS_DOMAIN일 때만 값이 있음
public record EmailCheck(EmailWarning warning, String suggestedDomain) {

	private static final EmailCheck OK = new EmailCheck(null, null);
	private static final EmailCheck MISSING = new EmailCheck(EmailWarning.MISSING, null);

	public static EmailCheck ok() {
		return OK;
	}

	public static EmailCheck missing() {
		return MISSING;
	}

	public static EmailCheck suspicious(String suggestedDomain) {
		return new EmailCheck(EmailWarning.SUSPICIOUS_DOMAIN, suggestedDomain);
	}

	public boolean sendable() {
		return warning == null;
	}

}
