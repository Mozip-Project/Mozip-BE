package com.mozip.domain.file.dto;

import java.util.Set;

import com.mozip.domain.file.exception.FileErrorCode;
import com.mozip.global.exception.BusinessException;

// 업로드 용도별 저장 경로, 용량 제한, 허용 contentType (설계 초안 값이며 실제 정책 확정 시 조정)
public enum FilePurpose {

	ORG_LOGO("org-logo", 5 * 1024 * 1024, Set.of("image/png", "image/jpeg", "image/webp")),
	POSTING_IMAGE("posting-image", 10 * 1024 * 1024, Set.of("image/png", "image/jpeg", "image/webp")),
	ENROLLMENT_CERT("enrollment-cert", 10 * 1024 * 1024, Set.of("image/png", "image/jpeg", "application/pdf")),
	APPLICATION_ATTACHMENT("application-attachment", 20 * 1024 * 1024, Set.of("application/pdf"));

	private final String keyPrefix;
	private final long maxSizeBytes;
	private final Set<String> allowedContentTypes;

	FilePurpose(String keyPrefix, long maxSizeBytes, Set<String> allowedContentTypes) {
		this.keyPrefix = keyPrefix;
		this.maxSizeBytes = maxSizeBytes;
		this.allowedContentTypes = allowedContentTypes;
	}

	public String keyPrefix() {
		return keyPrefix;
	}

	public void validate(String contentType, long size) {
		if (size > maxSizeBytes) {
			throw new BusinessException(FileErrorCode.FILE_TOO_LARGE)
				.addContext("purpose", name())
				.addContext("size", size);
		}
		if (!allowedContentTypes.contains(contentType)) {
			throw new BusinessException(FileErrorCode.UNSUPPORTED_FILE_TYPE)
				.addContext("purpose", name())
				.addContext("contentType", contentType);
		}
	}

}
