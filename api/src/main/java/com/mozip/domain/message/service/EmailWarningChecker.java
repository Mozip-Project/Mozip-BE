package com.mozip.domain.message.service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.mozip.domain.message.dto.EmailCheck;

// 받는 사람 주소의 이메일 경고 판정. DB와 스프링 없이 입력만으로 결과가 정해지는 계산이라 단위 테스트로 검증함
public final class EmailWarningChecker {

	// 오타가 자주 나는 메일 서비스 도메인. 앞에 있을수록 먼저 추천됨
	private static final List<String> COMMON_DOMAINS = List.of("gmail.com", "naver.com", "daum.net", "hanmail.net",
		"kakao.com", "outlook.com");

	private final Set<String> knownDomains;

	// schoolDomains는 학교 메일 도메인 목록. null이나 빈 값이 섞여 있어도 됨
	public EmailWarningChecker(Collection<String> schoolDomains) {
		Set<String> domains = new LinkedHashSet<>(COMMON_DOMAINS);
		schoolDomains.stream()
			.filter(domain -> domain != null && !domain.isBlank())
			.map(EmailWarningChecker::normalize)
			.sorted()
			.forEach(domains::add);
		this.knownDomains = domains;
	}

	public EmailCheck check(String email) {
		String domain = domainOf(email);
		if (domain == null) {
			return EmailCheck.missing();
		}
		// 아는 도메인과 정확히 같으면 다른 도메인과 한 글자 차이여도 경고하지 않음 (cau.ac.kr와 cnu.ac.kr 등)
		if (knownDomains.contains(domain)) {
			return EmailCheck.ok();
		}
		return knownDomains.stream()
			.filter(known -> isOneEditApart(domain, known))
			.findFirst()
			.map(EmailCheck::suspicious)
			.orElseGet(EmailCheck::ok);
	}

	// local@domain 형식이 아니면 null
	private static String domainOf(String email) {
		if (email == null) {
			return null;
		}
		String trimmed = email.trim();
		int at = trimmed.lastIndexOf('@');
		if (at <= 0 || at == trimmed.length() - 1 || trimmed.chars().anyMatch(Character::isWhitespace)) {
			return null;
		}
		return normalize(trimmed.substring(at + 1));
	}

	private static String normalize(String domain) {
		return domain.trim().toLowerCase(Locale.ROOT);
	}

	// 편집 거리(글자 하나 추가, 삭제, 교체)가 정확히 1인지. 같은 문자열은 false
	private static boolean isOneEditApart(String a, String b) {
		if (Math.abs(a.length() - b.length()) > 1 || a.equals(b)) {
			return false;
		}
		String shorter = a.length() <= b.length() ? a : b;
		String longer = a.length() <= b.length() ? b : a;
		int i = 0;
		while (i < shorter.length() && shorter.charAt(i) == longer.charAt(i)) {
			i++;
		}
		// 처음 다른 자리 뒤가 전부 같으면 1. 길이가 같으면 교체, 다르면 긴 쪽에서 한 글자 삭제
		String shorterRest = shorter.substring(shorter.length() == longer.length() ? i + 1 : i);
		return shorterRest.equals(longer.substring(i + 1));
	}

}
