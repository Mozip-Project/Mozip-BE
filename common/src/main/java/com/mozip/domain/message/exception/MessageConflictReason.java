package com.mozip.domain.message.exception;

// MESSAGE_409의 상황 구분
public enum MessageConflictReason {

	// 서류 결과가 아직 확정되지 않은 공고 (posting.docResultConfirmedAt이 null)
	RESULT_NOT_CONFIRMED,
	// 이미 발송을 시작했거나 끝난 메시지(SENDING, SENT)를 수정하거나 다시 발송하려 함
	ALREADY_SENT,
	// 같은 (공고, type, target)의 첫 저장이 동시에 들어와 메시지가 막 생성됨. 다시 요청하면 됨
	CONCURRENT_SAVE

}
