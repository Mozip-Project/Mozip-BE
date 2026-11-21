package com.mozip.domain.interview.timetable;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// 면접 가능 구간을 블록(면접 1회 시간 + 쉬는 시간)으로 자름, 블록은 저장하지 않고 매번 계산
public final class InterviewSlotCalculator {

	private InterviewSlotCalculator() {
	}

	// 구간 끝에 면접 1회가 들어가지 않는 자투리는 버리고, 결과는 시간순
	public static List<Slot> calculate(List<TimeRange> ranges, InterviewSetting setting) {
		List<TimeRange> sorted = ranges.stream()
			.sorted(Comparator.comparing(TimeRange::date).thenComparing(TimeRange::startTime))
			.toList();
		rejectOverlap(sorted);
		List<Slot> slots = new ArrayList<>();
		for (TimeRange range : sorted) {
			LocalTime start = range.startTime();
			while (!start.plusMinutes(setting.interviewMinutes()).isAfter(range.endTime())
				&& fitsInDay(start, setting.interviewMinutes())) {
				LocalDateTime startAt = range.date().atTime(start);
				slots.add(new Slot(startAt, startAt.plusMinutes(setting.interviewMinutes()),
					range.closedSlots().contains(start)));
				if (!fitsInDay(start, setting.blockMinutes())) {
					break;
				}
				start = start.plusMinutes(setting.blockMinutes());
			}
		}
		return List.copyOf(slots);
	}

	// 같은 날 구간이 겹치면 거부 (맞닿은 구간은 허용)
	private static void rejectOverlap(List<TimeRange> sorted) {
		for (int i = 1; i < sorted.size(); i++) {
			TimeRange before = sorted.get(i - 1);
			TimeRange current = sorted.get(i);
			if (before.date().equals(current.date()) && current.startTime().isBefore(before.endTime())) {
				throw new IllegalArgumentException("같은 날 면접 가능 구간이 겹칩니다: " + current.date());
			}
		}
	}

	// start에서 minutes만큼이 자정을 넘지 않는지 확인
	private static boolean fitsInDay(LocalTime start, int minutes) {
		return start.toSecondOfDay() + minutes * 60L < 24 * 60 * 60L;
	}

}
