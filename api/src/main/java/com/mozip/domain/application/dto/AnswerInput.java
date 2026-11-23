package com.mozip.domain.application.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AnswerInput(
	@NotNull(message = "문항을 선택해주세요.") Long questionId,
	// TEXT는 그대로, SINGLE_CHOICE는 선택지 문자열 그대로, MULTI_CHOICE와 TABLE은 JSON 배열 문자열
	String content,
	// FILE_LINK 문항만. 개수 상한은 제출 때 maxFiles로 따로 검사하므로 여기는 넉넉하게만 막음
	@Size(max = 20, message = "첨부는 최대 20개까지 올릴 수 있어요")
	List<@Valid AttachmentInput> attachments
) {
}
