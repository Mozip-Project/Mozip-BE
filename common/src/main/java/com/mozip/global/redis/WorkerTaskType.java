package com.mozip.global.redis;

// WorkerChannel.TASKS로 보내는 메시지의 type 필드. api가 채워서 보내고 worker가 분기해서 처리함
public enum WorkerTaskType {
	SEND_MAIL
}
