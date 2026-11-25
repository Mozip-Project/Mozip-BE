package com.mozip.worker.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mozip.domain.mail.exception.MailErrorCode;
import com.mozip.global.exception.BusinessException;

import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;

// SesV2Client를 목으로 바꿔서, SendEmailRequest 변환과 실패 시 예외 전환만 확인 (DB/네트워크 없음)
@ExtendWith(MockitoExtension.class)
class SesMailSenderTest {

	@Mock
	private SesV2Client sesV2Client;

	private SesMailSender sesMailSender;

	@BeforeEach
	void setUp() {
		SesProperties sesProperties = new SesProperties("ap-northeast-2", "noreply@mo-zip.com", "모집");
		sesMailSender = new SesMailSender(sesV2Client, sesProperties);
	}

	@Test
	@DisplayName("to/subject/htmlBody/textBody를 SendEmailRequest로 올바르게 옮긴다")
	void buildsSendEmailRequestFromArguments() {
		sesMailSender.send("student@school.ac.kr", "제목", "<p>본문</p>", "텍스트 본문");

		ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
		verify(sesV2Client).sendEmail(captor.capture());
		SendEmailRequest request = captor.getValue();

		assertThat(request.fromEmailAddress()).isEqualTo("모집 <noreply@mo-zip.com>");
		assertThat(request.destination().toAddresses()).containsExactly("student@school.ac.kr");
		assertThat(request.content().simple().subject().data()).isEqualTo("제목");
		assertThat(request.content().simple().body().html().data()).isEqualTo("<p>본문</p>");
		assertThat(request.content().simple().body().text().data()).isEqualTo("텍스트 본문");
	}

	@Test
	@DisplayName("textBody가 null이면 Body에 text 파트를 안 넣는다")
	void omitsTextPartWhenNull() {
		sesMailSender.send("student@school.ac.kr", "제목", "<p>본문</p>", null);

		ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
		verify(sesV2Client).sendEmail(captor.capture());

		assertThat(captor.getValue().content().simple().body().text()).isNull();
	}

	@Test
	@DisplayName("SES 호출이 실패하면 MAIL_503으로 바꿔 던진다")
	void translatesSdkExceptionToBusinessException() {
		when(sesV2Client.sendEmail(any(SendEmailRequest.class)))
			.thenThrow(SesV2Exception.builder().message("limit exceeded").build());

		assertThatThrownBy(() -> sesMailSender.send("student@school.ac.kr", "제목", "<p>본문</p>", null))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(MailErrorCode.SEND_FAILED);
	}

}
