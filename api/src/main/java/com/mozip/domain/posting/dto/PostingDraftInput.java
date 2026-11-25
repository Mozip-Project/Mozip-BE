package com.mozip.domain.posting.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.mozip.domain.posting.entity.AssignRule;
import com.mozip.domain.posting.entity.InterviewMethod;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

// 4단계 입력을 한 타입에 전부 모아두고, 단계별로 그 단계에 해당하는 필드만 채워서 보냄 (나머지는 null = 기존 값 유지)
// 단계별 부분 저장과 같이 쓰므로 null은 통과하는 @Size/@Positive만 쓰고 @NotBlank/@NotNull은 붙이지 않음
public record PostingDraftInput(
	// 1단계 basic
	@Size(max = 100, message = "제목은 100자까지 입력할 수 있어요") String title,
	String content,
	@Size(max = 20, message = "학기는 20자까지 입력할 수 있어요") String semester,
	@Size(max = 100, message = "지역은 100자까지 입력할 수 있어요") String region,
	@Size(max = 50, message = "활동비는 50자까지 입력할 수 있어요") String fee,
	Boolean allowEnrolled,
	Boolean allowOnLeave,
	@Size(max = 200, message = "지원 자격은 200자까지 입력할 수 있어요") String eligibilityNote,
	LocalDate activityStartedOn,
	LocalDate activityEndedOn,
	LocalDateTime recruitStartAt,
	LocalDateTime recruitEndAt,
	List<@Valid PostingImageInput> images,

	// 2단계 positions
	Boolean noPosition,
	List<@Valid PositionInput> positions,

	// 3단계 interview
	Boolean hasInterview,
	InterviewMethod interviewMethod,
	@Positive(message = "면접 시간은 1분 이상이어야 해요") Integer interviewMinutes,
	@PositiveOrZero(message = "휴식 시간은 0분 이상이어야 해요") Integer breakMinutes,
	AssignRule assignRule,
	@Positive(message = "면접당 최대 인원은 1명 이상이어야 해요") Integer maxApplicantsPerInterview,
	@Positive(message = "동시 진행 면접 수는 1개 이상이어야 해요") Integer maxParallelInterviews,
	List<@Valid InterviewTimeRangeInput> interviewTimeRanges,

	// 4단계 questions
	Boolean docScorePerQuestion,
	List<@Valid QuestionInput> questions
) {
}
