package com.mozip.domain.file.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.domain.file.dto.FilePurpose;
import com.mozip.domain.file.dto.PresignedUrlRequest;
import com.mozip.domain.file.dto.PresignedUrlResponse;
import com.mozip.global.config.S3Properties;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FileService {

	private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM");

	private final S3Client s3Client;
	private final S3Presigner s3Presigner;
	private final S3Properties s3Properties;

	public PresignedUrlResponse issuePresignedUrl(PresignedUrlRequest request) {
		request.purpose().validate(request.contentType(), request.size());

		String fileKey = buildFileKey(request.purpose(), request.contentType());
		String uploadUrl = presignPut(fileKey, request.contentType(), request.size());
		String fileUrl = presignGet(fileKey);

		return new PresignedUrlResponse(uploadUrl, fileKey, fileUrl);
	}

	// 서명은 자격 증명으로 로컬에서 계산하므로(HMAC) S3 호출이 없어, 목록 조회에서 여러 번 불러도 괜찮음
	public String presignGet(String fileKey) {
		GetObjectRequest getObjectRequest = GetObjectRequest.builder()
			.bucket(s3Properties.bucket())
			.key(fileKey)
			.build();

		GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
			.signatureDuration(s3Properties.presignedGetExpiration())
			.getObjectRequest(getObjectRequest)
			.build();

		return s3Presigner.presignGetObject(presignRequest).url().toString();
	}

	// 업로드가 실제로 끝났는지, 바이트 크기가 얼마인지는 클라이언트가 신고한 값을 믿지 않고 S3에 직접 물어봄.
	// object가 없으면 S3Exception(NoSuchKey)이 그대로 올라가니 호출하는 쪽에서 잡아 BusinessException으로 바꿔야 함
	public long headObjectSize(String fileKey) {
		HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
			.bucket(s3Properties.bucket())
			.key(fileKey)
			.build();

		return s3Client.headObject(headObjectRequest).contentLength();
	}

	private String presignPut(String fileKey, String contentType, long contentLength) {
		PutObjectRequest putObjectRequest = PutObjectRequest.builder()
			.bucket(s3Properties.bucket())
			.key(fileKey)
			.contentType(contentType)
			.contentLength(contentLength)
			.build();

		PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
			.signatureDuration(s3Properties.presignedUrlExpiration())
			.putObjectRequest(putObjectRequest)
			.build();

		return s3Presigner.presignPutObject(presignRequest).url().toString();
	}

	private String buildFileKey(FilePurpose purpose, String contentType) {
		String datePath = LocalDate.now().format(DATE_PATH);
		return "%s/%s/%s%s".formatted(purpose.keyPrefix(), datePath, UUID.randomUUID(),
			purpose.extensionFor(contentType));
	}

}
