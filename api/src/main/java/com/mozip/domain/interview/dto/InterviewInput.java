package com.mozip.domain.interview.dto;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.Size;

// id가 있으면 그 칸, 없으면 startAt과 track이 가리키는 칸. null인 필드는 그대로 둠
public record InterviewInput(
	Long id,
	LocalDateTime startAt,
	Integer track,
	@Size(max = 100) String location,
	List<Long> applicationIds,
	List<Long> interviewerMemberIds
) {
}
