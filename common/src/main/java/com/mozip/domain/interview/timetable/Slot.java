package com.mozip.domain.interview.timetable;

import java.time.LocalDateTime;

// 타임테이블의 블록 한 칸. endAt은 면접이 끝나는 시각이고 쉬는 시간은 그 뒤에 붙음. closed면 배정하지 않음
public record Slot(LocalDateTime startAt, LocalDateTime endAt, boolean closed) {
}
