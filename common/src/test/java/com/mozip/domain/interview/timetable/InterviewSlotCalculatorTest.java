package com.mozip.domain.interview.timetable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


class InterviewSlotCalculatorTest {

	private static final LocalDate DAY1 = LocalDate.of(2026, 1, 5);
	private static final LocalDate DAY2 = LocalDate.of(2026, 1, 6);

	@Test
	@DisplayName("구간을 면접 1회 시간 + 쉬는 시간 단위로 자르고 끝의 자투리는 버린다")
	void splitsWithBreak() {
		// 10:00~11:10, 20분 면접 + 10분 쉬는 시간 → 10:00, 10:30 (11:00~11:20은 11:10을 넘어 버림)
		List<Slot> slots = InterviewSlotCalculator.calculate(
			List.of(new TimeRange(DAY1, LocalTime.of(10, 0), LocalTime.of(11, 10), Set.of())),
			setting(20, 10));

		assertThat(slots).extracting(Slot::startAt, Slot::endAt).containsExactly(
			org.assertj.core.groups.Tuple.tuple(at(DAY1, 10, 0), at(DAY1, 10, 20)),
			org.assertj.core.groups.Tuple.tuple(at(DAY1, 10, 30), at(DAY1, 10, 50)));
	}

	@Test
	@DisplayName("마지막 블록은 뒤에 쉬는 시간이 없어도 면접만 들어가면 포함된다")
	void lastBlockNeedsNoTrailingBreak() {
		// 10:00~10:50, 20 + 10 → 10:30~10:50 블록은 쉬는 시간까지 치면 11:00이라 넘치지만 면접은 딱 들어가서 포함
		List<Slot> slots = InterviewSlotCalculator.calculate(
			List.of(new TimeRange(DAY1, LocalTime.of(10, 0), LocalTime.of(10, 50), Set.of())),
			setting(20, 10));

		assertThat(slots).extracting(Slot::startAt).containsExactly(at(DAY1, 10, 0), at(DAY1, 10, 30));
	}

	@Test
	@DisplayName("쉬는 시간이 0이면 면접이 연달아 붙는다")
	void noBreak() {
		List<Slot> slots = InterviewSlotCalculator.calculate(
			List.of(new TimeRange(DAY1, LocalTime.of(13, 0), LocalTime.of(14, 0), Set.of())),
			setting(15, 0));

		assertThat(slots).hasSize(4);
		assertThat(slots.getLast().startAt()).isEqualTo(at(DAY1, 13, 45));
		assertThat(slots.getLast().endAt()).isEqualTo(at(DAY1, 14, 0));
	}

	@Test
	@DisplayName("구간이 여러 개면 날짜, 시작 시각 순으로 이어 붙인다")
	void multipleRangesSorted() {
		List<Slot> slots = InterviewSlotCalculator.calculate(List.of(
				new TimeRange(DAY2, LocalTime.of(9, 0), LocalTime.of(10, 0), Set.of()),
				new TimeRange(DAY1, LocalTime.of(14, 0), LocalTime.of(15, 0), Set.of()),
				new TimeRange(DAY1, LocalTime.of(10, 0), LocalTime.of(11, 0), Set.of())),
			setting(30, 0));

		assertThat(slots).extracting(Slot::startAt).containsExactly(
			at(DAY1, 10, 0), at(DAY1, 10, 30), at(DAY1, 14, 0), at(DAY1, 14, 30), at(DAY2, 9, 0), at(DAY2, 9, 30));
	}

	@Test
	@DisplayName("운영진이 끈 블록은 closed로 표시되고 블록 자체는 남는다")
	void closedSlots() {
		List<Slot> slots = InterviewSlotCalculator.calculate(
			List.of(new TimeRange(DAY1, LocalTime.of(10, 0), LocalTime.of(11, 30), Set.of(LocalTime.of(10, 30)))),
			setting(30, 0));

		assertThat(slots).extracting(Slot::startAt, Slot::closed).containsExactly(
			org.assertj.core.groups.Tuple.tuple(at(DAY1, 10, 0), false),
			org.assertj.core.groups.Tuple.tuple(at(DAY1, 10, 30), true),
			org.assertj.core.groups.Tuple.tuple(at(DAY1, 11, 0), false));
	}

	@Test
	@DisplayName("면접이 하나도 들어가지 않는 짧은 구간은 블록이 없다")
	void tooShortRange() {
		List<Slot> slots = InterviewSlotCalculator.calculate(
			List.of(new TimeRange(DAY1, LocalTime.of(10, 0), LocalTime.of(10, 20), Set.of())),
			setting(30, 10));

		assertThat(slots).isEmpty();
	}

	@Test
	@DisplayName("자정 직전까지 연 구간도 하루를 넘기지 않고 끝난다")
	void endsBeforeMidnight() {
		List<Slot> slots = InterviewSlotCalculator.calculate(
			List.of(new TimeRange(DAY1, LocalTime.of(23, 0), LocalTime.of(23, 59), Set.of())),
			setting(20, 10));

		assertThat(slots).extracting(Slot::startAt).containsExactly(at(DAY1, 23, 0), at(DAY1, 23, 30));
	}

	@Test
	@DisplayName("같은 날 구간이 겹치면 거부하고 맞닿은 구간은 이어 붙인다")
	void overlappingRanges() {
		assertThatThrownBy(() -> InterviewSlotCalculator.calculate(List.of(
				new TimeRange(DAY1, LocalTime.of(10, 0), LocalTime.of(12, 0), Set.of()),
				new TimeRange(DAY1, LocalTime.of(11, 0), LocalTime.of(13, 0), Set.of())),
			setting(30, 0))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> InterviewSlotCalculator.calculate(List.of(
				new TimeRange(DAY1, LocalTime.of(10, 0), LocalTime.of(11, 0), Set.of()),
				new TimeRange(DAY1, LocalTime.of(10, 0), LocalTime.of(11, 0), Set.of())),
			setting(30, 0))).isInstanceOf(IllegalArgumentException.class);

		List<Slot> slots = InterviewSlotCalculator.calculate(List.of(
				new TimeRange(DAY1, LocalTime.of(11, 0), LocalTime.of(12, 0), Set.of()),
				new TimeRange(DAY1, LocalTime.of(10, 0), LocalTime.of(11, 0), Set.of()),
				new TimeRange(DAY2, LocalTime.of(10, 0), LocalTime.of(11, 0), Set.of())),
			setting(30, 0));
		assertThat(slots).extracting(Slot::startAt).containsExactly(
			at(DAY1, 10, 0), at(DAY1, 10, 30), at(DAY1, 11, 0), at(DAY1, 11, 30), at(DAY2, 10, 0), at(DAY2, 10, 30));
	}

	@Test
	@DisplayName("시작이 끝보다 늦은 구간과 0 이하 설정값은 거부한다")
	void invalidInput() {
		assertThatThrownBy(() -> new TimeRange(DAY1, LocalTime.of(11, 0), LocalTime.of(10, 0), Set.of()))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> setting(0, 10)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> setting(20, -1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new InterviewSetting(20, 10, 0, 1))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new InterviewSetting(20, 10, 1, 0))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private static InterviewSetting setting(int interviewMinutes, int breakMinutes) {
		return new InterviewSetting(interviewMinutes, breakMinutes, 3, 1);
	}

	private static LocalDateTime at(LocalDate date, int hour, int minute) {
		return date.atTime(hour, minute);
	}

}
