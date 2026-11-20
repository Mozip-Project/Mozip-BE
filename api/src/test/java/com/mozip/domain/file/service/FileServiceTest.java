package com.mozip.domain.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mozip.domain.file.dto.FilePurpose;
import com.mozip.domain.file.dto.PresignedUrlRequest;
import com.mozip.domain.file.dto.PresignedUrlResponse;
import com.mozip.domain.file.exception.FileErrorCode;
import com.mozip.global.config.S3Properties;
import com.mozip.global.exception.BusinessException;

import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

class FileServiceTest {

	private final S3Presigner s3Presigner = mock(S3Presigner.class);
	private final S3Properties s3Properties = new S3Properties("mozip-test", "ap-northeast-2", Duration.ofMinutes(10),
		"https://cdn.mozip.kr");
	private final FileService fileService = new FileService(s3Presigner, s3Properties);

	@Test
	@DisplayName("purpose가 허용하지 않는 contentType이면 FILE_415를 던진다")
	void unsupportedContentType() {
		PresignedUrlRequest request = new PresignedUrlRequest(FilePurpose.ORG_LOGO, "logo.gif", "image/gif", 1000L);

		assertThatThrownBy(() -> fileService.issuePresignedUrl(request))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(FileErrorCode.UNSUPPORTED_FILE_TYPE);
	}

	@Test
	@DisplayName("purpose별 허용 용량을 초과하면 FILE_413을 던진다")
	void fileTooLarge() {
		PresignedUrlRequest request = new PresignedUrlRequest(FilePurpose.ORG_LOGO, "logo.png", "image/png",
			10 * 1024 * 1024L);

		assertThatThrownBy(() -> fileService.issuePresignedUrl(request))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(FileErrorCode.FILE_TOO_LARGE);
	}

	@Test
	@DisplayName("정상 요청이면 purpose의 keyPrefix로 시작하는 fileKey와 cdnDomain 기반 fileUrl을 반환한다")
	void issuesPresignedUrl() throws Exception {
		PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
		when(presigned.url()).thenReturn(URI.create("https://s3.example.com/presigned").toURL());
		when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);

		PresignedUrlRequest request = new PresignedUrlRequest(FilePurpose.POSTING_IMAGE, "poster.png", "image/png",
			1000L);

		PresignedUrlResponse response = fileService.issuePresignedUrl(request);

		assertThat(response.uploadUrl()).isEqualTo("https://s3.example.com/presigned");
		assertThat(response.fileKey()).startsWith("posting-image/").endsWith(".png");
		assertThat(response.fileUrl()).isEqualTo("https://cdn.mozip.kr/" + response.fileKey());
	}

}
