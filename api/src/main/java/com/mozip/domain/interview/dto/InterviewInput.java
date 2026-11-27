package com.mozip.domain.interview.dto;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.Size;

// id가 없으면 startAt 블록에 면접 칸을 새로 추가, 있으면 그 칸을 수정. null인 필드는 그대로 둠
public record InterviewInput(
	Long id,
	LocalDateTime startAt,
	@Size(max = 100) String location,
	List<Long> applicationIds,
	List<Long> interviewerMemberIds
) {
}
