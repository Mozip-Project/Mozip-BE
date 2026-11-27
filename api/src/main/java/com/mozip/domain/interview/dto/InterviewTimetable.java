package com.mozip.domain.interview.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.mozip.domain.application.entity.Application;
import com.mozip.domain.interview.entity.Interview;
import com.mozip.domain.interview.timetable.Slot;
import com.mozip.domain.posting.entity.Posting;

// 운영진이 보는 타임테이블 화면 한 장. slots는 빈 칸까지 포함한 시간 행, interviews는 지원자가 배정된 칸
public record InterviewTimetable(Posting posting, LocalDateTime confirmedAt, boolean editing, int trackCount,
	List<Slot> slots, List<Interview> interviews, List<Application> unassignedApplicants,
	List<String> recentLocations) {
}
