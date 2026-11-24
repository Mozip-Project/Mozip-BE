package com.mozip.domain.message.service;

import com.mozip.domain.message.dto.MessageTemplate;
import com.mozip.domain.message.entity.MessageTarget;

// 결과 안내 기본 문구. 운영진이 고치기 전 초안과 초기화에 쓰이며 {이름}은 발송 때 지원자별로 치환됨
public final class ResultMessageTemplates {

	private static final String DOC_RESULT_SUBJECT = "%s 서류 전형 결과 안내";
	private static final String DOC_RESULT_PASS_BODY = "{이름}님, %s 서류 전형에 합격하셨습니다. "
		+ "이후 일정은 추후 안내드리겠습니다. 궁금한 점은 언제든 문의해 주세요.";
	private static final String DOC_RESULT_FAIL_BODY = "{이름}님, %s에 관심을 갖고 지원해 주셔서 감사합니다. "
		+ "아쉽게도 이번 서류 전형에서는 함께하지 못하게 되었습니다. 다음 기회에 다시 만나길 바랍니다.";

	private ResultMessageTemplates() {
	}

	public static MessageTemplate docResult(MessageTarget target, String organizationName) {
		String body = target == MessageTarget.PASS ? DOC_RESULT_PASS_BODY : DOC_RESULT_FAIL_BODY;
		return new MessageTemplate(DOC_RESULT_SUBJECT.formatted(organizationName), body.formatted(organizationName));
	}

}
