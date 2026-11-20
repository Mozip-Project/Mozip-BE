package com.mozip.domain.file.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.mozip.domain.file.dto.FilePurpose;
import com.mozip.domain.file.dto.PresignedUrlRequest;
import com.mozip.domain.file.dto.PresignedUrlResponse;
import com.mozip.global.config.S3Properties;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@RequiredArgsConstructor
public class FileService {

	private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM");

	private final S3Presigner s3Presigner;
	private final S3Properties s3Properties;

	public PresignedUrlResponse issuePresignedUrl(PresignedUrlRequest request) {
		request.purpose().validate(request.contentType(), request.size());

		String fileKey = buildFileKey(request.purpose(), request.fileName());
		String uploadUrl = presignPut(fileKey, request.contentType());
		String fileUrl = s3Properties.cdnDomain() + "/" + fileKey;

		return new PresignedUrlResponse(uploadUrl, fileKey, fileUrl);
	}

	private String presignPut(String fileKey, String contentType) {
		PutObjectRequest putObjectRequest = PutObjectRequest.builder()
			.bucket(s3Properties.bucket())
			.key(fileKey)
			.contentType(contentType)
			.build();

		PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
			.signatureDuration(s3Properties.presignedUrlExpiration())
			.putObjectRequest(putObjectRequest)
			.build();

		return s3Presigner.presignPutObject(presignRequest).url().toString();
	}

	private String buildFileKey(FilePurpose purpose, String fileName) {
		String datePath = LocalDate.now().format(DATE_PATH);
		return "%s/%s/%s%s".formatted(purpose.keyPrefix(), datePath, UUID.randomUUID(), extensionOf(fileName));
	}

	private String extensionOf(String fileName) {
		int dotIndex = fileName.lastIndexOf('.');
		return dotIndex >= 0 ? fileName.substring(dotIndex) : "";
	}

}
