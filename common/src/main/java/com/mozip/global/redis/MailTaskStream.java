package com.mozip.global.redis;

// 운영진이 보내는 일반 메일(결과 안내 등) 작업 전용 스트림. api가 XADD로 넣고 worker가 컨슈머 그룹으로 읽음.
// 실패하면 ACK 안 하고 남겨서 재시도하고, 다 소진하면 DLQ_KEY로 옮김(최초 시도 포함 총 4번, worker MailTaskRetrySweeper).
// 학교 인증 메일(SCHOOL_VERIFICATION_MAIL)은 재시도 가치가 없어(코드가 곧 만료되고 재요청이 쉬움) 그대로 WorkerChannel(pub/sub)을 씀
public final class MailTaskStream {

	public static final String KEY = "mozip:worker:mail-tasks";
	public static final String DLQ_KEY = "mozip:worker:mail-tasks:dlq";
	public static final String CONSUMER_GROUP = "worker";
	// worker 인스턴스가 지금은 1개뿐이라 고정값. 여러 개로 늘리면 호스트명/PID 기반으로 바꿔야 함
	public static final String CONSUMER_NAME = "worker-1";

	private MailTaskStream() {
	}

}
