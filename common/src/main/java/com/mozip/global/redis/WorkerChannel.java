package com.mozip.global.redis;

// api -> worker pub/sub 채널 이름. api와 worker 양쪽에서 쓰므로 common에 둠
public final class WorkerChannel {

	public static final String TASKS = "mozip:worker:tasks";

	private WorkerChannel() {
	}

}
