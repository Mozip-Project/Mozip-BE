package com.mozip.domain.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mozip.domain.file.dto.FilePurpose;
import com.mozip.domain.file.dto.PresignedUrlRequest;
import com.mozip.domain.file.dto.PresignedUrlResponse;
import com.mozip.domain.file.exception.FileErrorCode;
import com.mozip.global.config.S3Properties;
import com.mozip.global.exception.BusinessException;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

class FileServiceTest {

	private final S3Client s3Client = mock(S3Client.class);
	private final S3Presigner s3Presigner = mock(S3Presigner.class);
	private final S3Properties s3Properties = new S3Properties("mozip-test", "ap-northeast-2", Duration.ofMinutes(10),
		Duration.ofMinutes(10));
	private final FileService fileService = new FileService(s3Client, s3Presigner, s3Properties);

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
	@DisplayName("정상 요청이면 purpose의 keyPrefix로 시작하는 fileKey와 GET presigned previewUrl을 반환한다")
	void issuesPresignedUrl() throws Exception {
		stubPresignPut("https://s3.example.com/presigned");
		stubPresignGet("https://s3.example.com/preview");

		PresignedUrlRequest request = new PresignedUrlRequest(FilePurpose.POSTING_IMAGE, "poster.png", "image/png",
			1000L);

		PresignedUrlResponse response = fileService.issuePresignedUrl(request);

		assertThat(response.uploadUrl()).isEqualTo("https://s3.example.com/presigned");
		assertThat(response.fileKey()).startsWith("posting-image/").endsWith(".png");
		assertThat(response.previewUrl()).isEqualTo("https://s3.example.com/preview");
	}

	@Test
	@DisplayName("신고한 size가 서명의 contentLength로 들어가 다른 크기의 업로드를 S3가 거부하게 만든다")
	void signsWithReportedContentLength() throws Exception {
		ArgumentCaptor<PutObjectPresignRequest> captor = ArgumentCaptor.forClass(PutObjectPresignRequest.class);
		PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
		when(presigned.url()).thenReturn(URI.create("https://s3.example.com/presigned").toURL());
		when(s3Presigner.presignPutObject(captor.capture())).thenReturn(presigned);
		stubPresignGet("https://s3.example.com/preview");

		PresignedUrlRequest request = new PresignedUrlRequest(FilePurpose.POSTING_IMAGE, "poster.png", "image/png",
			1000L);

		fileService.issuePresignedUrl(request);

		verify(s3Presigner).presignPutObject(any(PutObjectPresignRequest.class));
		assertThat(captor.getValue().putObjectRequest().contentLength()).isEqualTo(1000L);
	}

	@Test
	@DisplayName("fileName의 확장자가 contentType과 달라도 fileKey는 contentType이 정한 확장자로 끝난다")
	void fileKeyExtensionComesFromContentTypeNotFileName() throws Exception {
		stubPresignPut("https://s3.example.com/presigned");
		stubPresignGet("https://s3.example.com/preview");

		PresignedUrlRequest request = new PresignedUrlRequest(FilePurpose.POSTING_IMAGE,
			"malicious.html/../../etc", "image/png", 1000L);

		PresignedUrlResponse response = fileService.issuePresignedUrl(request);

		assertThat(response.fileKey()).startsWith("posting-image/").endsWith(".png");
		assertThat(response.fileKey()).doesNotContain("..", "etc", "html");
	}

	@Test
	@DisplayName("presignGet은 S3를 호출하지 않고 로컬 서명만으로 GET presigned URL을 만든다")
	void presignsGetWithoutCallingS3() throws Exception {
		stubPresignGet("https://s3.example.com/read");

		String url = fileService.presignGet("posting-image/2026/01/a.png");

		assertThat(url).isEqualTo("https://s3.example.com/read");
	}

	@Test
	@DisplayName("headObjectSize는 S3에 HeadObject를 보내 contentLength를 그대로 반환한다")
	void headObjectSizeReturnsContentLength() {
		HeadObjectResponse response = mock(HeadObjectResponse.class);
		when(response.contentLength()).thenReturn(204800L);
		when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(response);

		long size = fileService.headObjectSize("application-attachment/2026/02/a.pdf");

		assertThat(size).isEqualTo(204800L);
	}

	private void stubPresignPut(String url) throws Exception {
		PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
		when(presigned.url()).thenReturn(URI.create(url).toURL());
		when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);
	}

	private void stubPresignGet(String url) throws Exception {
		PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
		when(presigned.url()).thenReturn(URI.create(url).toURL());
		when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);
	}

}
