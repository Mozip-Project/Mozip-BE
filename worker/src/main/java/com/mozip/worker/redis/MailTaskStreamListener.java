package com.mozip.worker.redis;

import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

// MailTaskStream.KEY를 컨슈머 그룹으로 읽어 발송. 실패 시 재시도는 MailTaskRetrySweeper가 담당
@Component
@RequiredArgsConstructor
class MailTaskStreamListener implements StreamListener<String, MapRecord<String, String, String>> {

	private final MailTaskProcessor processor;

	@Override
	public void onMessage(MapRecord<String, String, String> record) {
		processor.process(record);
	}

}
