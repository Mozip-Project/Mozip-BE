package com.mozip.worker.mail;

// 메일 발송 공통 인터페이스. 학교 인증번호 메일(school)과 결과 안내 메일(message, B)이 같이 씀
public interface MailSender {

	void send(String to, String subject, String body);

}
