package com.mozip.worker.redis;

// api -> worker pub/sub 채널 이름 (api 쪽에도 동일한 이름으로 publish 설정 필요)
public final class WorkerChannel {

	public static final String TASKS = "mozip:worker:tasks";

	private WorkerChannel() {
	}

}
