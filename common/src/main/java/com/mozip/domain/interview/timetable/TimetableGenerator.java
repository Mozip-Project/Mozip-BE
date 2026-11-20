package com.mozip.domain.interview.timetable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.mozip.domain.posting.entity.AssignRule;

// 서류 합격자를 블록에 배정. 같은 입력이면 같은 결과가 나오도록 정렬된 목록만 돌고 순서 없는 자료구조는 쓰지 않음
public final class TimetableGenerator {

	private TimetableGenerator() {
	}

	// 블록을 시간순으로 돌며 track을 1부터 maxParallelInterviews까지 열고, track마다 그 시각에 가능한 지원자를 정원까지 채움
	// 먼저 제출한 지원자부터 자리를 잡고, 한 칸에는 첫 지원자와 같은 포지션만 함께 들어감
	// teamByPosition은 포지션 id → 팀 id (SAME_TEAM 면접관 선정용)
	public static TimetableResult generate(List<Slot> slots, InterviewSetting setting, List<Candidate> candidates,
		List<Panelist> panelists, Map<Long, Long> teamByPosition) {
		List<Candidate> remaining = new ArrayList<>(candidates.stream()
			.sorted(Comparator.comparing(Candidate::submittedAt, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(Candidate::applicationId))
			.toList());
		List<Slot> orderedSlots = slots.stream()
			.filter(slot -> !slot.closed())
			.sorted(Comparator.comparing(Slot::startAt))
			.toList();
		List<AssignedInterview> interviews = new ArrayList<>();

		for (Slot slot : orderedSlots) {
			for (int track = 1; track <= setting.maxParallelInterviews() && !remaining.isEmpty(); track++) {
				List<Candidate> group = pickGroup(remaining, slot, setting.maxApplicantsPerInterview());
				if (group.isEmpty()) {
					break;
				}
				remaining.removeAll(group);
				Long positionId = group.getFirst().positionId();
				interviews.add(new AssignedInterview(slot.startAt(), slot.endAt(), track, positionId,
					group.stream().map(Candidate::applicationId).toList(),
					selectInterviewers(panelists, setting.assignRule(), positionId, teamByPosition)));
			}
		}
		return new TimetableResult(interviews, remaining.stream().map(Candidate::applicationId).toList());
	}

	// 그 시각에 가능한 첫 지원자를 잡고, 같은 포지션이면서 가능한 지원자를 정원까지 이어 붙임
	private static List<Candidate> pickGroup(List<Candidate> remaining, Slot slot, int capacity) {
		List<Candidate> group = new ArrayList<>();
		for (Candidate candidate : remaining) {
			if (!candidate.isAvailableAt(slot.startAt())) {
				continue;
			}
			if (group.isEmpty() || group.getFirst().hasSamePosition(candidate)) {
				group.add(candidate);
				if (group.size() == capacity) {
					break;
				}
			}
		}
		return group;
	}

	// 포지션 없는 모집(positionId null)은 자동 배정하지 않음 (스키마 AssignRule 설명과 같음)
	private static List<Long> selectInterviewers(List<Panelist> panelists, AssignRule rule, Long positionId,
		Map<Long, Long> teamByPosition) {
		if (positionId == null) {
			return List.of();
		}
		if (rule == AssignRule.ALL_MEMBERS) {
			return panelists.stream().map(Panelist::memberId).toList();
		}
		Long teamId = teamByPosition.get(positionId);
		if (teamId == null) {
			return List.of();
		}
		return panelists.stream()
			.filter(panelist -> teamId.equals(panelist.teamId()))
			.map(Panelist::memberId)
			.toList();
	}

}
