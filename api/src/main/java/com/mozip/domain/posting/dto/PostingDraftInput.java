package com.mozip.domain.posting.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.mozip.domain.posting.entity.AssignRule;
import com.mozip.domain.posting.entity.InterviewMethod;

// 5단계 입력을 한 타입에 전부 모아두고, 단계별로 그 단계에 해당하는 필드만 채워서 보냄 (나머지는 null = 기존 값 유지)
public record PostingDraftInput(
	// 1단계 basic
	String title,
	String content,
	String semester,
	String region,
	String fee,
	Boolean allowEnrolled,
	Boolean allowOnLeave,
	String eligibilityNote,
	LocalDate activityStartedOn,
	LocalDate activityEndedOn,
	LocalDateTime recruitStartAt,
	LocalDateTime recruitEndAt,
	List<PostingImageInput> images,

	// 2단계 positions
	Boolean noPosition,
	List<PositionInput> positions,

	// 3단계 interview
	Boolean hasInterview,
	InterviewMethod interviewMethod,
	Integer interviewMinutes,
	Integer breakMinutes,
	AssignRule assignRule,
	Integer maxApplicantsPerInterview,
	Integer maxParallelInterviews,
	List<InterviewTimeRangeInput> interviewTimeRanges,

	// 4단계 questions
	Boolean docScorePerQuestion,
	List<QuestionInput> questions,

	// 5단계 exposure
	LocalDateTime publishAt,
	Long representativeImageId
) {
}
