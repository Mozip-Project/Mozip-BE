package com.mozip.global.redis;

// 메일 작업 메시지의 type 필드. 전달 방식이 둘로 나뉨
public enum WorkerTaskType {
	// MailTaskStream(Redis Stream)으로 보냄. 실패하면 최대 3번 재시도 후 DLQ로 이동
	SEND_MAIL,
	// WorkerChannel.TASKS(pub/sub)로 보냄. 재시도 없음. worker가 보낸 뒤 school:verify:status:{email}에 성공/실패만 남김
	SCHOOL_VERIFICATION_MAIL
}
