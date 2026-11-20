package com.mozip.domain.interview.timetable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.mozip.domain.posting.entity.AssignRule;

// 서류 합격자를 블록에 배정. 같은 입력이면 같은 결과가 나오도록 정렬된 목록만 돌고 결과 순서를 순서 없는 자료구조에 맡기지 않음
public final class TimetableGenerator {

	private static final Comparator<Candidate> PRIORITY = Comparator
		.comparing(Candidate::submittedAt, Comparator.nullsLast(Comparator.naturalOrder()))
		.thenComparing(Candidate::applicationId);

	private TimetableGenerator() {
	}

	// 먼저 제출한 지원자부터 한 명씩 자리를 잡음. 빈자리가 없으면 이미 앉은 지원자를 다른 블록으로 옮겨 자리를 만듦 (증가 경로)
	// 한 번 배정된 지원자는 옮겨질 수는 있어도 빠지지 않아서, 늦게 낸 지원자 때문에 먼저 낸 지원자가 미배정되는 일은 없음
	// teamByPosition은 포지션 id → 팀 id (SAME_TEAM 면접관 선정용)
	public static TimetableResult generate(List<Slot> slots, InterviewSetting setting, List<Candidate> candidates,
		List<Panelist> panelists, Map<Long, Long> teamByPosition) {
		validate(candidates);
		List<Candidate> ordered = candidates.stream().sorted(PRIORITY).toList();
		List<Slot> openSlots = slots.stream()
			.filter(slot -> !slot.closed())
			.sorted(Comparator.comparing(Slot::startAt))
			.toList();

		Assignment assignment = new Assignment(openSlots, setting);
		List<Long> unassigned = new ArrayList<>();
		for (Candidate candidate : ordered) {
			if (!assignment.place(candidate, new HashSet<>())) {
				unassigned.add(candidate.applicationId());
			}
		}
		return new TimetableResult(assignment.toInterviews(panelists, teamByPosition), unassigned);
	}

	private static void validate(List<Candidate> candidates) {
		Set<Long> ids = new HashSet<>();
		for (Candidate candidate : candidates) {
			Objects.requireNonNull(candidate.applicationId(), "applicationId는 null일 수 없습니다.");
			if (!ids.add(candidate.applicationId())) {
				throw new IllegalArgumentException("같은 지원서가 두 번 들어왔습니다: " + candidate.applicationId());
			}
		}
	}

	// 블록마다 앉은 지원자 목록. 블록 하나에는 포지션별로 정원씩 묶은 칸이 동시 진행 수까지 들어감
	private static final class Assignment {

		private final List<Slot> slots;
		private final List<List<Candidate>> seated;
		private final int capacity;
		private final int maxParallel;
		private final AssignRule assignRule;

		private Assignment(List<Slot> slots, InterviewSetting setting) {
			this.slots = slots;
			this.seated = new ArrayList<>();
			slots.forEach(slot -> seated.add(new ArrayList<>()));
			this.capacity = setting.maxApplicantsPerInterview();
			this.maxParallel = setting.maxParallelInterviews();
			this.assignRule = setting.assignRule();
		}

		// 빈자리가 있는 가장 이른 블록에 먼저 앉히고, 없으면 블록마다 늦게 낸 지원자부터 비켜 보며 옮길 곳을 재귀로 찾음
		// visited는 이번 탐색에서 이미 비켜 본 블록이라 같은 블록을 두 번 뒤지지 않음 (블록 수만큼만 깊어짐)
		private boolean place(Candidate candidate, Set<Integer> visited) {
			List<Integer> available = new ArrayList<>();
			for (int i = 0; i < slots.size(); i++) {
				if (!visited.contains(i) && candidate.isAvailableAt(slots.get(i).startAt())) {
					available.add(i);
				}
			}
			for (int i : available) {
				if (fits(seated.get(i), null, candidate)) {
					seat(i, candidate);
					return true;
				}
			}
			for (int i : available) {
				visited.add(i);
				List<Candidate> current = new ArrayList<>(seated.get(i));
				for (Candidate occupant : current.reversed()) {
					if (!fits(current, occupant, candidate)) {
						continue;
					}
					seated.get(i).remove(occupant);
					seat(i, candidate);
					if (place(occupant, visited)) {
						return true;
					}
					seated.get(i).remove(candidate);
					seat(i, occupant);
				}
			}
			return false;
		}

		private void seat(int slotIndex, Candidate candidate) {
			List<Candidate> list = seated.get(slotIndex);
			list.add(candidate);
			list.sort(PRIORITY);
		}

		// 포지션마다 필요한 칸 수(정원으로 나눠 올림)의 합이 동시 진행 수 이하여야 함. 포지션 없는 모집은 null 키 하나로 묶임
		private boolean fits(List<Candidate> current, Candidate leaving, Candidate joining) {
			Map<Long, Integer> countByPosition = new LinkedHashMap<>();
			for (Candidate seatedCandidate : current) {
				if (seatedCandidate != leaving) {
					countByPosition.merge(seatedCandidate.positionId(), 1, Integer::sum);
				}
			}
			countByPosition.merge(joining.positionId(), 1, Integer::sum);
			int tracks = countByPosition.values().stream().mapToInt(count -> (count + capacity - 1) / capacity).sum();
			return tracks <= maxParallel;
		}

		// 블록마다 포지션별로 먼저 낸 순서대로 정원씩 묶어 track 1부터 매김. 포지션 순서는 그 포지션에서 가장 먼저 낸 지원자 기준
		private List<AssignedInterview> toInterviews(List<Panelist> panelists, Map<Long, Long> teamByPosition) {
			List<AssignedInterview> interviews = new ArrayList<>();
			for (int i = 0; i < slots.size(); i++) {
				Slot slot = slots.get(i);
				List<List<Candidate>> groups = groups(seated.get(i));
				List<List<Long>> interviewers = interviewers(groups, panelists, teamByPosition);
				for (int track = 0; track < groups.size(); track++) {
					List<Candidate> group = groups.get(track);
					interviews.add(new AssignedInterview(slot.startAt(), slot.endAt(), track + 1,
						group.getFirst().positionId(), group.stream().map(Candidate::applicationId).toList(),
						interviewers.get(track)));
				}
			}
			return interviews;
		}

		private List<List<Candidate>> groups(List<Candidate> seatedInSlot) {
			Map<Long, List<Candidate>> byPosition = new LinkedHashMap<>();
			for (Candidate candidate : seatedInSlot) {
				byPosition.computeIfAbsent(candidate.positionId(), key -> new ArrayList<>()).add(candidate);
			}
			List<List<Candidate>> groups = new ArrayList<>();
			for (List<Candidate> samePosition : byPosition.values()) {
				for (int from = 0; from < samePosition.size(); from += capacity) {
					groups.add(samePosition.subList(from, Math.min(from + capacity, samePosition.size())));
				}
			}
			return groups;
		}

		// 같은 시각의 칸들이 같은 면접관 후보를 쓰면 후보를 앞에서부터 나눠 맡김. 한 사람이 동시에 두 면접에 들어가지 않게 함
		private List<List<Long>> interviewers(List<List<Candidate>> groups, List<Panelist> panelists,
			Map<Long, Long> teamByPosition) {
			List<List<Long>> pools = groups.stream()
				.map(group -> pool(panelists, group.getFirst().positionId(), teamByPosition))
				.toList();
			List<List<Long>> result = new ArrayList<>();
			for (int track = 0; track < groups.size(); track++) {
				List<Long> pool = pools.get(track);
				int sharing = 0;
				int order = 0;
				for (int other = 0; other < groups.size(); other++) {
					if (pools.get(other).equals(pool)) {
						if (other < track) {
							order++;
						}
						sharing++;
					}
				}
				result.add(share(pool, sharing, order));
			}
			return result;
		}

		// 앞 칸이 한 명씩 더 가져가도록 거의 같은 크기로 나눔
		private static List<Long> share(List<Long> pool, int parts, int index) {
			int base = pool.size() / parts;
			int extra = pool.size() % parts;
			int from = index * base + Math.min(index, extra);
			int size = base + (index < extra ? 1 : 0);
			return pool.subList(from, from + size);
		}

		// 포지션 없는 모집(positionId null)은 자동 배정하지 않음 (스키마 AssignRule 설명과 같음)
		private List<Long> pool(List<Panelist> panelists, Long positionId, Map<Long, Long> teamByPosition) {
			if (positionId == null) {
				return List.of();
			}
			if (assignRule == AssignRule.ALL_MEMBERS) {
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

}
