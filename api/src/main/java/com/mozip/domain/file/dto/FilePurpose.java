package com.mozip.domain.file.dto;

import java.util.Map;

import com.mozip.domain.file.exception.FileErrorCode;
import com.mozip.global.exception.BusinessException;

// 업로드 용도별 저장 경로, 용량 제한, 허용 contentType과 그에 대응하는 확장자 (설계 초안 값이며 실제 정책 확정 시 조정)
public enum FilePurpose {

	ORG_LOGO("org-logo", 5 * 1024 * 1024, Map.of(
		"image/png", ".png",
		"image/jpeg", ".jpg",
		"image/webp", ".webp",
		"image/svg+xml", ".svg")),
	POSTING_IMAGE("posting-image", 10 * 1024 * 1024, Map.of(
		"image/png", ".png",
		"image/jpeg", ".jpg",
		"image/webp", ".webp")),
	ENROLLMENT_CERT("enrollment-cert", 10 * 1024 * 1024, Map.of(
		"image/png", ".png",
		"image/jpeg", ".jpg",
		"application/pdf", ".pdf")),
	APPLICATION_ATTACHMENT("application-attachment", 20 * 1024 * 1024, Map.of(
		"application/pdf", ".pdf"));

	private final String keyPrefix;
	private final long maxSizeBytes;
	private final Map<String, String> extensionsByContentType;

	FilePurpose(String keyPrefix, long maxSizeBytes, Map<String, String> extensionsByContentType) {
		this.keyPrefix = keyPrefix;
		this.maxSizeBytes = maxSizeBytes;
		this.extensionsByContentType = extensionsByContentType;
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
		if (!extensionsByContentType.containsKey(contentType)) {
			throw new BusinessException(FileErrorCode.UNSUPPORTED_FILE_TYPE)
				.addContext("purpose", name())
				.addContext("contentType", contentType);
		}
	}

	// validate()로 contentType을 먼저 검증한 뒤에 부른다
	public String extensionFor(String contentType) {
		return extensionsByContentType.get(contentType);
	}

}
