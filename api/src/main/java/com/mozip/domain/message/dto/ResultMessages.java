package com.mozip.domain.message.dto;

import com.mozip.domain.posting.entity.Posting;

// 결과 안내 화면 전체. resultMessages 조회와 sendMessages 응답이 같은 모양을 씀
public record ResultMessages(Posting posting, ResultMessagePanel pass, ResultMessagePanel fail) {
}
