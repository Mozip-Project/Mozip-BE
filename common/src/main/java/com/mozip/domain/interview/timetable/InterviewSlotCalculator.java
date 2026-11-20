package com.mozip.domain.interview.timetable;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// 면접 가능 구간을 블록(면접 1회 시간 + 쉬는 시간)으로 자름. 블록은 DB에 저장하지 않고 조회할 때마다 같은 식으로 다시 계산
public final class InterviewSlotCalculator {

	private InterviewSlotCalculator() {
	}

	// 구간 끝에 면접 1회가 통째로 들어가지 않는 자투리는 버림 (마지막 블록 뒤의 쉬는 시간은 없어도 됨)
	// 날짜, 시작 시각 순으로 돌아 결과는 항상 시간순
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

	// 같은 날 구간이 겹치면 같은 시각 블록이 두 번 생겨 한 사람이 동시에 두 면접에 배정될 수 있어 입력 단계에서 막음
	// 맞닿은 구간(10~12시, 12~14시)은 겹침이 아님
	private static void rejectOverlap(List<TimeRange> sorted) {
		for (int i = 1; i < sorted.size(); i++) {
			TimeRange before = sorted.get(i - 1);
			TimeRange current = sorted.get(i);
			if (before.date().equals(current.date()) && current.startTime().isBefore(before.endTime())) {
				throw new IllegalArgumentException("같은 날 면접 가능 구간이 겹칩니다: " + current.date());
			}
		}
	}

	// LocalTime 덧셈은 자정을 넘으면 다음 날로 감겨 무한 루프가 되므로 하루 안에 드는지 먼저 확인
	private static boolean fitsInDay(LocalTime start, int minutes) {
		return start.toSecondOfDay() + minutes * 60L < 24 * 60 * 60L;
	}

}
