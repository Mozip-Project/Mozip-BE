package com.mozip.domain.interview.timetable;

// 면접관 후보 운영진. teamId는 팀 없는 단체나 팀 미지정이면 null
public record Panelist(Long memberId, Long teamId) {
}
