package com.mozip.domain.application.dto;

import java.util.List;

public record AnswerInput(
	Long questionId,
	// TEXT 답변, CHOICE / TABLE은 JSON 문자열
	String content,
	// FILE_LINK 문항만
	List<AttachmentInput> attachments
) {
}
