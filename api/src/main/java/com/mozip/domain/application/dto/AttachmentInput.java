package com.mozip.domain.application.dto;

import com.mozip.domain.application.entity.AttachmentType;

public record AttachmentInput(
	AttachmentType type,
	// FILE = presigned URL(purpose=APPLICATION_ATTACHMENT)로 올린 파일의 키/URL, LINK = 지원자가 입력한 링크
	String url,
	String fileName
) {
}
