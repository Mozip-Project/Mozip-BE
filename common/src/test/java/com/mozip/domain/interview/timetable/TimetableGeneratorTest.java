package com.mozip.domain.interview.timetable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TimetableGeneratorTest {

	private static final LocalDate DAY = LocalDate.of(2026, 1, 5);
	private static final LocalDateTime T10 = DAY.atTime(10, 0);
	private static final LocalDateTime T10_30 = DAY.atTime(10, 30);
	private static final LocalDateTime T11 = DAY.atTime(11, 0);
	private static final long DEV = 1L;
	private static final long DESIGN = 2L;

	// 10:00 / 10:30 / 11:00 세 블록 (30분 면접, 쉬는 시간 0)
	private static final List<Slot> SLOTS = InterviewSlotCalculator.calculate(
		List.of(new TimeRange(DAY, LocalTime.of(10, 0), LocalTime.of(11, 30), Set.of())),
		new InterviewSetting(30, 0, 1, 1));

	@Test
	@DisplayName("먼저 제출한 지원자부터 가능한 블록에 정원만큼 채우고, 넘치면 다음 블록으로 간다")
	void fillsByCapacityThenNextSlot() {
		List<Candidate> candidates = List.of(
			candidate(1L, DEV, 3, T10, T10_30),
			candidate(2L, DEV, 1, T10, T10_30),
			candidate(3L, DEV, 2, T10, T10_30));

		TimetableResult result = TimetableGenerator.generate(SLOTS, setting(2, 1),
			candidates);

		assertThat(result.interviews()).extracting(AssignedInterview::startAt, AssignedInterview::track,
			AssignedInterview::applicationIds).containsExactly(
			tuple(T10, 1, List.of(2L, 3L)),
			tuple(T10_30, 1, List.of(1L)));
		assertThat(result.unassignedApplicationIds()).isEmpty();
	}

	@Test
	@DisplayName("같은 시각에 여러 지원자가 가능하면 동시 진행 수까지 track을 연다")
	void opensParallelTracks() {
		List<Candidate> candidates = List.of(
			candidate(1L, DEV, 1, T10),
			candidate(2L, DEV, 2, T10),
			candidate(3L, DEV, 3, T10));

		TimetableResult result = TimetableGenerator.generate(SLOTS, setting(1, 2),
			candidates);

		assertThat(result.interviews()).extracting(AssignedInterview::startAt, AssignedInterview::track,
			AssignedInterview::applicationIds).containsExactly(
			tuple(T10, 1, List.of(1L)),
			tuple(T10, 2, List.of(2L)));
		assertThat(result.unassignedApplicationIds()).containsExactly(3L);
	}

	@Test
	@DisplayName("먼저 낸 지원자가 다른 시각도 되면 그쪽으로 옮겨 늦게 낸 지원자 자리를 만든다")
	void relocatesEarlierCandidate() {
		// 1번은 10:00/10:30 둘 다, 2번은 10:00만 가능. 먼저 온 순서대로만 채우면 1번이 10:00을 잡아 2번이 미배정됨
		List<Candidate> candidates = List.of(candidate(1L, DEV, 1, T10, T10_30), candidate(2L, DEV, 2, T10));

		TimetableResult result = TimetableGenerator.generate(SLOTS, setting(1, 1),
			candidates);

		assertThat(result.interviews()).extracting(AssignedInterview::startAt, AssignedInterview::applicationIds)
			.containsExactly(tuple(T10, List.of(2L)), tuple(T10_30, List.of(1L)));
		assertThat(result.unassignedApplicationIds()).isEmpty();
	}

	@Test
	@DisplayName("포지션이 달라 한 칸에 못 묶일 때도 옮길 수 있는 지원자를 옮겨 모두 배정한다")
	void relocatesAcrossPositions() {
		List<Candidate> candidates = List.of(
			candidate(1L, DEV, 1, T10, T10_30),
			candidate(2L, DESIGN, 2, T10),
			candidate(3L, DESIGN, 3, T10));

		TimetableResult result = TimetableGenerator.generate(SLOTS, setting(2, 1),
			candidates);

		assertThat(result.interviews()).extracting(AssignedInterview::startAt, AssignedInterview::positionId,
			AssignedInterview::applicationIds).containsExactly(
			tuple(T10, DESIGN, List.of(2L, 3L)),
			tuple(T10_30, DEV, List.of(1L)));
		assertThat(result.unassignedApplicationIds()).isEmpty();
	}

	@Test
	@DisplayName("자리가 모자라면 먼저 낸 지원자가 남고 늦게 낸 지원자가 미배정된다")
	void earlierSubmissionKeepsSeat() {
		List<Candidate> candidates = List.of(candidate(2L, DEV, 2, T10), candidate(1L, DEV, 1, T10));

		TimetableResult result = TimetableGenerator.generate(SLOTS, setting(1, 1),
			candidates);

		assertThat(result.interviews()).extracting(AssignedInterview::applicationIds)
			.containsExactly(List.of(1L));
		assertThat(result.unassignedApplicationIds()).containsExactly(2L);
	}

	@Test
	@DisplayName("지원서 id가 비었거나 겹치면 거부한다")
	void rejectsInvalidCandidates() {
		InterviewSetting setting = setting(1, 1);

		assertThatThrownBy(() -> TimetableGenerator.generate(SLOTS, setting,
			List.of(candidate(1L, DEV, 1, T10), candidate(1L, DEV, 2, T10_30))))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> TimetableGenerator.generate(SLOTS, setting,
			List.of(new Candidate(null, DEV, T10, Set.of(T10)))))
			.isInstanceOf(NullPointerException.class);
	}

	@Test
	@DisplayName("한 칸에는 같은 포지션 지원자만 들어가고 다른 포지션은 다음 track이나 블록으로 간다")
	void groupsBySamePosition() {
		List<Candidate> candidates = List.of(
			candidate(1L, DEV, 1, T10),
			candidate(2L, DESIGN, 2, T10),
			candidate(3L, DEV, 3, T10));

		TimetableResult result = TimetableGenerator.generate(SLOTS, setting(3, 2),
			candidates);

		assertThat(result.interviews()).extracting(AssignedInterview::track, AssignedInterview::positionId,
			AssignedInterview::applicationIds).containsExactly(
			tuple(1, DEV, List.of(1L, 3L)),
			tuple(2, DESIGN, List.of(2L)));
	}

	@Test
	@DisplayName("끈 블록은 건너뛰고, 가능한 시각이 없는 지원자는 미배정으로 남는다")
	void skipsClosedAndReportsUnassigned() {
		List<Slot> slots = List.of(
			new Slot(T10, T10.plusMinutes(30), true),
			new Slot(T10_30, T10_30.plusMinutes(30), false));
		List<Candidate> candidates = List.of(
			candidate(1L, DEV, 1, T10),
			candidate(2L, DEV, 2, T10_30),
			candidate(3L, DEV, 3));

		TimetableResult result = TimetableGenerator.generate(slots, setting(3, 1),
			candidates);

		assertThat(result.interviews()).extracting(AssignedInterview::startAt, AssignedInterview::applicationIds)
			.containsExactly(tuple(T10_30, List.of(2L)));
		assertThat(result.unassignedApplicationIds()).containsExactly(1L, 3L);
	}

	@Test
	@DisplayName("포지션 없는 모집은 포지션 구분 없이 한 칸에 묶어 배정한다")
	void noPositionGroupsTogether() {
		List<Candidate> candidates = List.of(candidate(1L, null, 1, T10), candidate(2L, null, 2, T10));

		TimetableResult result = TimetableGenerator.generate(SLOTS, setting(2, 1),
			candidates);

		assertThat(result.interviews()).singleElement().satisfies(interview -> {
			assertThat(interview.positionId()).isNull();
			assertThat(interview.applicationIds()).containsExactly(1L, 2L);
		});
	}

	@Test
	@DisplayName("블록이나 지원자 순서를 섞어 넣어도 같은 결과가 나온다")
	void deterministic() {
		List<Candidate> candidates = new ArrayList<>(List.of(
			candidate(5L, DEV, 5, T10, T11), candidate(4L, DESIGN, 4, T10, T10_30), candidate(3L, DEV, 3, T10_30),
			candidate(2L, DEV, 2, T10, T10_30, T11), candidate(1L, DESIGN, 1, T10_30, T11)));
		InterviewSetting setting = setting(2, 2);
		TimetableResult expected = TimetableGenerator.generate(SLOTS, setting, candidates);

		List<Slot> shuffledSlots = new ArrayList<>(SLOTS);
		Collections.reverse(shuffledSlots);
		Collections.shuffle(candidates, new Random(42));

		assertThat(TimetableGenerator.generate(shuffledSlots, setting, candidates))
			.isEqualTo(expected);
		assertThat(expected.unassignedApplicationIds()).isEmpty();
	}

	@Test
	@DisplayName("블록이 없으면 전원 미배정이다")
	void noSlots() {
		TimetableResult result = TimetableGenerator.generate(List.of(), setting(1, 1),
			List.of(candidate(1L, DEV, 1, T10)));

		assertThat(result.interviews()).isEmpty();
		assertThat(result.unassignedApplicationIds()).containsExactly(1L);
	}

	private static InterviewSetting setting(int maxApplicants, int maxParallel) {
		return new InterviewSetting(30, 0, maxApplicants, maxParallel);
	}

	// submittedOrder가 작을수록 먼저 제출한 지원자
	private static Candidate candidate(long applicationId, Long positionId, int submittedOrder,
		LocalDateTime... availableTimes) {
		return new Candidate(applicationId, positionId, DAY.minusDays(10).atTime(9, 0).plusMinutes(submittedOrder),
			Set.of(availableTimes));
	}

}
