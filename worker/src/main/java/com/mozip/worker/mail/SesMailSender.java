package com.mozip.worker.mail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.mozip.domain.mail.exception.MailErrorCode;
import com.mozip.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "mail.provider", havingValue = "ses")
public class SesMailSender implements MailSender {

	private static final String CHARSET = "UTF-8";

	private final SesV2Client sesV2Client;
	private final SesProperties sesProperties;

	@Override
	public void send(String to, String subject, String body) {
		SendEmailRequest request = SendEmailRequest.builder()
			.fromEmailAddress(fromAddress())
			.destination(Destination.builder().toAddresses(to).build())
			.content(EmailContent.builder()
				.simple(Message.builder()
					.subject(content(subject))
					.body(Body.builder().text(content(body)).build())
					.build())
				.build())
			.build();
		try {
			sesV2Client.sendEmail(request);
		} catch (SdkException e) {
			// 한도 초과, 일시 장애 등. worker가 이 작업을 best-effort로 처리하므로 여기서 던져도 호출자에게는 안 전달됨(로그만 남음)
			log.error("SES 메일 발송 실패: to={}, cause={}, message={}", to, e.getClass().getSimpleName(), e.getMessage());
			throw new BusinessException(MailErrorCode.SEND_FAILED).addContext("cause", e.getClass().getSimpleName());
		}
	}

	private static Content content(String data) {
		return Content.builder().data(data).charset(CHARSET).build();
	}

	private String fromAddress() {
		return "%s <%s>".formatted(sesProperties.senderName(), sesProperties.senderAddress());
	}

}
