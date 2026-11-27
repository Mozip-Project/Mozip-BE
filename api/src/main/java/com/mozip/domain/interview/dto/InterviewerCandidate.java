package com.mozip.domain.interview.dto;

import com.mozip.domain.interview.entity.Interview;
import com.mozip.domain.organization.entity.Member;

// 면접관 추가 목록의 한 줄. busyInterview가 있으면 그 시간에 다른 면접에 들어가 있어 고를 수 없음
public record InterviewerCandidate(Member member, Interview busyInterview) {
}
