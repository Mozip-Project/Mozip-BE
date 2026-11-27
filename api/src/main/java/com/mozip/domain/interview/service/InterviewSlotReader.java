package com.mozip.domain.interview.service;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.springframework.stereotype.Component;

import com.mozip.domain.interview.timetable.InterviewSetting;
import com.mozip.domain.interview.timetable.InterviewSlotCalculator;
import com.mozip.domain.interview.timetable.Slot;
import com.mozip.domain.interview.timetable.TimeRange;
import com.mozip.domain.posting.entity.InterviewTimeRange;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.repository.InterviewTimeRangeRepository;

import lombok.RequiredArgsConstructor;

// 공고의 면접 설정과 운영진이 연 시간대를 읽어 블록으로 자름. 블록은 저장하지 않아 매번 계산
@Component
@RequiredArgsConstructor
class InterviewSlotReader {

	private final InterviewTimeRangeRepository interviewTimeRangeRepository;

	// 면접 설정이 다 채워지지 않았으면 empty
	Optional<InterviewSetting> setting(Posting posting) {
		if (posting.getInterviewMinutes() == null || posting.getBreakMinutes() == null
			|| posting.getMaxApplicantsPerInterview() == null || posting.getMaxParallelInterviews() == null) {
			return Optional.empty();
		}
		return Optional.of(new InterviewSetting(posting.getInterviewMinutes(), posting.getBreakMinutes(),
			posting.getMaxApplicantsPerInterview(), posting.getMaxParallelInterviews()));
	}

	// filter에 걸린 시간대만 블록으로 자름. 닫힌 블록도 closed로 표시해서 같이 돌려줌
	List<Slot> slots(Posting posting, InterviewSetting setting, Predicate<InterviewTimeRange> filter) {
		List<TimeRange> ranges = interviewTimeRangeRepository.findAllByPostingOrderByDateAscStartTimeAsc(posting)
			.stream()
			.filter(filter)
			.map(this::toTimeRange)
			.toList();
		if (ranges.isEmpty()) {
			return List.of();
		}
		return InterviewSlotCalculator.calculate(ranges, setting);
	}

	private TimeRange toTimeRange(InterviewTimeRange range) {
		List<LocalTime> closedSlots = range.getClosedSlots();
		return new TimeRange(range.getDate(), range.getStartTime(), range.getEndTime(),
			closedSlots == null ? Set.of() : Set.copyOf(closedSlots));
	}

}
