package com.mozip.domain.application.dto;

import java.util.List;

public record AnswerInput(
	Long questionId,
	// TEXT는 그대로, SINGLE_CHOICE는 선택지 문자열 그대로, MULTI_CHOICE와 TABLE은 JSON 배열 문자열
	String content,
	// FILE_LINK 문항만
	List<AttachmentInput> attachments
) {
}
