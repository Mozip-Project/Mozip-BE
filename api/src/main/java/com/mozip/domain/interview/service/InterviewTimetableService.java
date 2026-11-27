package com.mozip.domain.interview.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.domain.application.entity.Application;
import com.mozip.domain.application.entity.ApplicationStatus;
import com.mozip.domain.application.entity.DocResult;
import com.mozip.domain.application.repository.ApplicationRepository;
import com.mozip.domain.interview.dto.InterviewInput;
import com.mozip.domain.interview.dto.InterviewTimetable;
import com.mozip.domain.interview.dto.InterviewerCandidate;
import com.mozip.domain.interview.entity.Interview;
import com.mozip.domain.interview.entity.InterviewApplicant;
import com.mozip.domain.interview.entity.InterviewSnapshot;
import com.mozip.domain.interview.entity.InterviewTimetableState;
import com.mozip.domain.interview.entity.Interviewer;
import com.mozip.domain.interview.exception.InterviewConflictReason;
import com.mozip.domain.interview.exception.InterviewErrorCode;
import com.mozip.domain.interview.exception.InterviewInputReason;
import com.mozip.domain.interview.repository.InterviewApplicantRepository;
import com.mozip.domain.interview.repository.InterviewRepository;
import com.mozip.domain.interview.repository.InterviewSnapshotRepository;
import com.mozip.domain.interview.repository.InterviewTimetableStateRepository;
import com.mozip.domain.interview.repository.InterviewerRepository;
import com.mozip.domain.interview.timetable.AssignedInterview;
import com.mozip.domain.interview.timetable.Candidate;
import com.mozip.domain.interview.timetable.InterviewSetting;
import com.mozip.domain.interview.timetable.Slot;
import com.mozip.domain.interview.timetable.TimetableGenerator;
import com.mozip.domain.interview.timetable.TimetableResult;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.service.OrganizationAccessChecker;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.exception.PostingErrorCode;
import com.mozip.domain.posting.repository.PostingRepository;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;

import lombok.RequiredArgsConstructor;

// 운영진의 면접 타임테이블 조회, 자동 생성, 직접 배정, 확정, 확정 후 수정 모드
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InterviewTimetableService {

	// 미배정 목록은 먼저 낸 순. 자동 배정의 우선순위와 같음
	private static final Comparator<Application> SUBMITTED_ORDER = Comparator
		.comparing(Application::getSubmittedAt, Comparator.nullsLast(Comparator.naturalOrder()))
		.thenComparing(Application::getId);

	private final PostingRepository postingRepository;
	private final ApplicationRepository applicationRepository;
	private final InterviewRepository interviewRepository;
	private final InterviewApplicantRepository interviewApplicantRepository;
	private final InterviewerRepository interviewerRepository;
	private final InterviewTimetableStateRepository interviewTimetableStateRepository;
	private final InterviewSnapshotRepository interviewSnapshotRepository;
	private final InterviewSlotReader interviewSlotReader;
	private final OrganizationAccessChecker organizationAccessChecker;
	private final MemberRepository memberRepository;
	private final Clock clock;

	public InterviewTimetable find(Long userId, Long postingId) {
		Posting posting = findPosting(postingId);
		requireOperator(userId, posting);
		return load(posting);
	}

	// 면접관 추가 목록. 그 시각에 다른 면접(다른 공고 포함)에 들어가 있는 운영진은 busyInterview가 채워짐
	// interviewId는 지금 편집 중인 칸. 그 칸에 이미 있는 면접관은 바쁜 것으로 치지 않음
	public List<InterviewerCandidate> interviewerCandidates(Long userId, Long postingId, LocalDateTime startAt,
		Long interviewId) {
		Posting posting = findPosting(postingId);
		requireOperator(userId, posting);
		requireHasInterview(posting);
		InterviewSetting setting = requireSetting(posting);
		List<Member> members = memberRepository.findAllActiveWithUserAndTeamByOrganizationId(
			posting.getOrganization().getId());
		if (members.isEmpty()) {
			return List.of();
		}
		Map<Long, Interview> busyByMemberId = new LinkedHashMap<>();
		interviewerRepository.findAllOverlapping(members.stream().map(Member::getId).toList(),
				interviewId == null ? -1L : interviewId, startAt, startAt.plusMinutes(setting.interviewMinutes()))
			.forEach(interviewer -> busyByMemberId.putIfAbsent(interviewer.getMember().getId(),
				interviewer.getInterview()));
		return members.stream()
			.map(member -> new InterviewerCandidate(member, busyByMemberId.get(member.getId())))
			.toList();
	}

	// 서류 합격자를 면접 가능 시간에 맞춰 배정. 다시 부르면 직접 배정, 옮긴 칸, 면접관, 장소가 모두 지워짐
	// 운영진이 추가한 면접실 열은 남겨서 그 개수만큼 동시에 배정함
	@Transactional
	public InterviewTimetable generate(Long userId, Long postingId) {
		Posting posting = lockOperablePosting(userId, postingId);
		// 확정된 타임테이블은 통째로 다시 만들 수 없음. 고치려면 수정 모드에서 직접 옮김
		if (posting.getTimetableConfirmedAt() != null) {
			throw new BusinessException(InterviewErrorCode.CONFLICT, InterviewConflictReason.TIMETABLE_CONFIRMED)
				.addContext("postingId", postingId);
		}
		InterviewSetting base = requireSetting(posting);
		InterviewSetting setting = new InterviewSetting(base.interviewMinutes(), base.breakMinutes(),
			base.maxApplicantsPerInterview(), trackCount(posting, findState(posting), Optional.of(base)));

		List<Slot> slots = interviewSlotReader.slots(posting, setting, range -> true);
		List<Application> passed = passedApplications(posting);
		TimetableResult result = TimetableGenerator.generate(slots, setting,
			passed.stream().map(this::toCandidate).toList());

		interviewerRepository.deleteAllByPosting(posting);
		interviewApplicantRepository.deleteAllByPosting(posting);
		interviewRepository.deleteAllByPosting(posting);

		Map<Long, Application> applicationById = passed.stream()
			.collect(Collectors.toMap(Application::getId, Function.identity()));
		for (AssignedInterview assigned : result.interviews()) {
			Interview interview = interviewRepository.save(
				Interview.create(posting, assigned.startAt(), assigned.endAt(), assigned.track()));
			for (Long applicationId : assigned.applicationIds()) {
				interviewApplicantRepository.save(
					InterviewApplicant.create(interview, applicationById.get(applicationId)));
			}
		}
		return load(posting);
	}

	// 칸 하나에 지원자, 면접관, 장소를 넣음. 칸은 id로 가리키거나 startAt과 track으로 가리킴
	// 직접 배정은 지원자가 고른 시각, 포지션, 정원을 따지지 않음. 지원자가 없어진 칸은 지움
	// 확정 뒤에는 장소만 바로 바꿀 수 있고 나머지는 수정 모드에서만 바꿈
	@Transactional
	public InterviewTimetable update(Long userId, Long postingId, InterviewInput input) {
		Posting posting = lockOperablePosting(userId, postingId);
		Optional<InterviewTimetableState> state = findState(posting);
		if (input.id() == null || input.applicationIds() != null || input.interviewerMemberIds() != null) {
			requireEditable(posting, state);
		}
		Interview interview = input.id() == null
			? findOrCreateCell(posting, state, input)
			: findInterview(posting, input.id());
		if (input.location() != null) {
			interview.changeLocation(normalizeLocation(input.location()));
		}
		if (input.applicationIds() != null) {
			replaceApplicants(posting, interview, input.applicationIds().stream().distinct().toList());
		}
		if (input.interviewerMemberIds() != null) {
			replaceInterviewers(posting, interview, input.interviewerMemberIds().stream().distinct().toList());
		}
		interviewRepository.flush();
		deleteEmptyInterviews(posting);
		return load(findPosting(postingId));
	}

	// 머리글의 [장소 한 번에 입력]. 그 날짜, 그 면접실 열의 모든 칸 장소를 바꿈. 확정 뒤 보기 모드에서도 됨
	@Transactional
	public InterviewTimetable updateLocations(Long userId, Long postingId, LocalDate date, int track,
		String location) {
		Posting posting = lockOperablePosting(userId, postingId);
		String normalized = location == null ? null : normalizeLocation(location);
		interviewRepository.findAllByPostingOrderByStartAtAscTrackAscIdAsc(posting).stream()
			.filter(interview -> interview.getTrack() == track && interview.getStartAt().toLocalDate().equals(date))
			.forEach(interview -> interview.changeLocation(normalized));
		// 최근 장소 조회에 바로 보이게 함
		interviewRepository.flush();
		return load(posting);
	}

	// [면접 추가]. 면접실 열을 하나 늘림. 늘린 열은 다시 생성할 때도 쓰임
	@Transactional
	public InterviewTimetable addTrack(Long userId, Long postingId) {
		Posting posting = lockOperablePosting(userId, postingId);
		Optional<InterviewTimetableState> found = findState(posting);
		requireEditable(posting, found);
		int current = trackCount(posting, found, Optional.of(requireSetting(posting)));
		InterviewTimetableState state = found.orElseGet(
			() -> interviewTimetableStateRepository.save(InterviewTimetableState.create(posting, current)));
		state.changeTrackCount(current + 1);
		interviewTimetableStateRepository.flush();
		return load(posting);
	}

	// [수정하기]. 확정된 타임테이블의 사본을 떠 두고 수정 모드로 들어감. 이미 수정 모드면 그대로 돌려줌
	@Transactional
	public InterviewTimetable startEdit(Long userId, Long postingId) {
		Posting posting = lockOperablePosting(userId, postingId);
		if (posting.getTimetableConfirmedAt() == null) {
			throw new BusinessException(InterviewErrorCode.CONFLICT, InterviewConflictReason.NOT_CONFIRMED)
				.addContext("postingId", postingId);
		}
		Optional<InterviewTimetableState> found = findState(posting);
		if (found.filter(InterviewTimetableState::isEditing).isPresent()) {
			return load(posting);
		}
		int current = trackCount(posting, found, interviewSlotReader.setting(posting));
		InterviewTimetableState state = found.orElseGet(
			() -> interviewTimetableStateRepository.save(InterviewTimetableState.create(posting, current)));
		state.changeTrackCount(current);

		List<Interview> interviews = interviewRepository.findAllByPostingOrderByStartAtAscTrackAscIdAsc(posting);
		Map<Long, List<Long>> applicationIds = interviews.isEmpty() ? Map.of()
			: interviewApplicantRepository.findAllWithApplicationByInterviewIn(interviews).stream()
				.collect(Collectors.groupingBy(applicant -> applicant.getInterview().getId(),
					Collectors.mapping(applicant -> applicant.getApplication().getId(), Collectors.toList())));
		Map<Long, List<Long>> memberIds = interviews.isEmpty() ? Map.of()
			: interviewerRepository.findAllWithMemberByInterviewIn(interviews).stream()
				.collect(Collectors.groupingBy(interviewer -> interviewer.getInterview().getId(),
					Collectors.mapping(interviewer -> interviewer.getMember().getId(), Collectors.toList())));
		interviewSnapshotRepository.deleteAllByPosting(posting);
		for (Interview interview : interviews) {
			interviewSnapshotRepository.save(InterviewSnapshot.of(interview,
				applicationIds.getOrDefault(interview.getId(), List.of()),
				memberIds.getOrDefault(interview.getId(), List.of())));
		}
		state.startEditing(LocalDateTime.now(clock));
		interviewTimetableStateRepository.flush();
		return load(posting);
	}

	// [수정 취소]. 수정 모드에서 바꾼 배정, 면접관, 장소, 면접실 열을 버리고 확정했던 상태로 되돌림
	@Transactional
	public InterviewTimetable cancelEdit(Long userId, Long postingId) {
		Posting posting = lockOperablePosting(userId, postingId);
		InterviewTimetableState state = findState(posting)
			.filter(InterviewTimetableState::isEditing)
			.orElseThrow(() -> new BusinessException(InterviewErrorCode.CONFLICT,
				InterviewConflictReason.NOT_EDITING).addContext("postingId", postingId));

		List<InterviewSnapshot> snapshots =
			interviewSnapshotRepository.findAllByPostingOrderByStartAtAscTrackAscIdAsc(posting);
		interviewerRepository.deleteAllByPosting(posting);
		interviewApplicantRepository.deleteAllByPosting(posting);
		interviewRepository.deleteAllByPosting(posting);

		Map<Long, Application> applicationById = applicationRepository.findAllById(
				snapshots.stream().flatMap(snapshot -> snapshot.getApplicationIds().stream()).distinct().toList())
			.stream().collect(Collectors.toMap(Application::getId, Function.identity()));
		// 그 사이 단체를 나간 운영진은 면접관으로 되살리지 않음
		Map<Long, Member> memberById = memberRepository.findAllById(
				snapshots.stream().flatMap(snapshot -> snapshot.getMemberIds().stream()).distinct().toList())
			.stream().filter(member -> member.getLeftAt() == null)
			.collect(Collectors.toMap(Member::getId, Function.identity()));
		for (InterviewSnapshot snapshot : snapshots) {
			Interview interview = Interview.create(posting, snapshot.getStartAt(), snapshot.getEndAt(),
				snapshot.getTrack());
			interview.changeLocation(snapshot.getLocation());
			interviewRepository.save(interview);
			for (Long applicationId : snapshot.getApplicationIds()) {
				Application application = applicationById.get(applicationId);
				if (application != null) {
					interviewApplicantRepository.save(InterviewApplicant.create(interview, application));
				}
			}
			for (Long memberId : snapshot.getMemberIds()) {
				Member member = memberById.get(memberId);
				if (member != null) {
					interviewerRepository.save(Interviewer.create(interview, member));
				}
			}
		}
		interviewSnapshotRepository.deleteAllByPosting(posting);
		state.cancelEditing();
		interviewRepository.flush();
		return load(posting);
	}

	// [확정하기]와 수정 모드의 [다시 확정하기]. 미배정 지원자가 남아 있으면 확정할 수 없음
	// 지원자 안내는 여기서 하지 않고 확정 뒤 장소를 채운 다음 결과 안내에서 따로 보냄
	@Transactional
	public InterviewTimetable confirm(Long userId, Long postingId) {
		Posting posting = lockOperablePosting(userId, postingId);
		Optional<InterviewTimetableState> state = findState(posting);
		boolean confirmed = posting.getTimetableConfirmedAt() != null;
		boolean editing = state.filter(InterviewTimetableState::isEditing).isPresent();
		if (confirmed && !editing) {
			throw new BusinessException(InterviewErrorCode.CONFLICT, InterviewConflictReason.ALREADY_CONFIRMED)
				.addContext("postingId", postingId);
		}
		Set<Long> assignedIds = new HashSet<>(interviewApplicantRepository.findApplicationIdsByPosting(posting));
		long unassigned = passedApplications(posting).stream()
			.filter(application -> !assignedIds.contains(application.getId()))
			.count();
		if (unassigned > 0) {
			throw new BusinessException(InterviewErrorCode.CONFLICT,
				InterviewConflictReason.UNASSIGNED_APPLICANT_REMAINS)
				.addContext("postingId", postingId)
				.addContext("unassignedCount", unassigned);
		}
		if (assignedIds.isEmpty()) {
			throw new BusinessException(InterviewErrorCode.CONFLICT, InterviewConflictReason.NO_ASSIGNED_APPLICANT)
				.addContext("postingId", postingId);
		}
		LocalDateTime now = LocalDateTime.now(clock);
		if (editing) {
			// 수정한 내용을 그대로 확정하므로 사본은 버림
			interviewSnapshotRepository.deleteAllByPosting(posting);
			state.get().finishEditing();
			interviewTimetableStateRepository.flush();
			interviewRepository.reconfirmTimetable(postingId, now);
		} else if (interviewRepository.confirmTimetable(postingId, now) == 0) {
			// 확정 전일 때만 채우는 조건부 UPDATE라 동시에 두 번 눌러도 한 번만 성공함
			throw new BusinessException(InterviewErrorCode.CONFLICT, InterviewConflictReason.ALREADY_CONFIRMED)
				.addContext("postingId", postingId);
		}
		// 위 UPDATE가 영속성 컨텍스트를 비워서 확정 시각이 들어간 공고를 다시 읽음
		return load(findPosting(postingId));
	}

	// id 없이 온 입력이 가리키는 칸. 그 시각, 그 열에 이미 칸이 있으면 그 칸을 쓰고 없으면 만듦
	private Interview findOrCreateCell(Posting posting, Optional<InterviewTimetableState> state,
		InterviewInput input) {
		LocalDateTime startAt = input.startAt();
		if (startAt == null) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT, InterviewInputReason.START_AT_REQUIRED)
				.addContext("postingId", posting.getId());
		}
		InterviewSetting setting = requireSetting(posting);
		Slot slot = interviewSlotReader.slots(posting, setting, range -> true).stream()
			.filter(candidate -> !candidate.closed() && candidate.startAt().equals(startAt))
			.findFirst()
			.orElseThrow(() -> new BusinessException(CommonErrorCode.INVALID_INPUT,
				InterviewInputReason.SLOT_NOT_OPEN)
				.addContext("postingId", posting.getId())
				.addContext("startAt", startAt));
		int trackCount = trackCount(posting, state, Optional.of(setting));
		Map<Integer, Interview> byTrack = interviewRepository.findAllByPostingOrderByStartAtAscTrackAscIdAsc(posting)
			.stream()
			.filter(interview -> interview.getStartAt().equals(startAt))
			.collect(Collectors.toMap(Interview::getTrack, Function.identity(), (first, second) -> first));
		Integer track = input.track();
		if (track == null) {
			// 열을 안 정했으면 그 시각에 비어 있는 가장 앞 열
			for (int candidate = 1; candidate <= trackCount && track == null; candidate++) {
				if (!byTrack.containsKey(candidate)) {
					track = candidate;
				}
			}
		}
		if (track == null || track < 1 || track > trackCount) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT, InterviewInputReason.TRACK_OUT_OF_RANGE)
				.addContext("postingId", posting.getId())
				.addContext("track", input.track())
				.addContext("trackCount", trackCount);
		}
		Interview existing = byTrack.get(track);
		if (existing != null) {
			return existing;
		}
		if (input.applicationIds() == null || input.applicationIds().isEmpty()) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT, InterviewInputReason.APPLICANTS_REQUIRED)
				.addContext("postingId", posting.getId());
		}
		return interviewRepository.save(Interview.create(posting, slot.startAt(), slot.endAt(), track));
	}

	private Interview findInterview(Posting posting, Long interviewId) {
		return interviewRepository.findById(interviewId)
			.filter(interview -> interview.getPosting().getId().equals(posting.getId()))
			.orElseThrow(() -> new BusinessException(InterviewErrorCode.NOT_FOUND)
				.addContext("postingId", posting.getId())
				.addContext("interviewId", interviewId));
	}

	// 이 칸의 지원자를 통째로 교체. 다른 칸에 있던 지원자는 옮겨 오고, 목록에서 빠진 지원자는 미배정이 됨
	private void replaceApplicants(Posting posting, Interview interview, List<Long> applicationIds) {
		Map<Long, Application> applicationById = applicationRepository.findAllById(applicationIds).stream()
			.collect(Collectors.toMap(Application::getId, Function.identity()));
		for (Long applicationId : applicationIds) {
			Application application = applicationById.get(applicationId);
			if (application == null || !application.getPosting().getId().equals(posting.getId())
				|| application.getStatus() != ApplicationStatus.SUBMITTED
				|| application.getDocResult() != DocResult.PASS) {
				throw new BusinessException(CommonErrorCode.INVALID_INPUT,
					InterviewInputReason.APPLICANT_NOT_ELIGIBLE)
					.addContext("postingId", posting.getId())
					.addContext("applicationId", applicationId);
			}
		}
		interviewApplicantRepository.deleteAllByInterview(interview);
		if (!applicationIds.isEmpty()) {
			interviewApplicantRepository.deleteAllByPostingAndApplicationIdIn(posting, applicationIds);
		}
		for (Long applicationId : applicationIds) {
			interviewApplicantRepository.save(
				InterviewApplicant.create(interview, applicationById.get(applicationId)));
		}
	}

	// 이 칸의 면접관을 통째로 교체. 같은 운영진이 시간이 겹치는 다른 칸에 있으면 거부
	private void replaceInterviewers(Posting posting, Interview interview, List<Long> memberIds) {
		Map<Long, Member> memberById = memberRepository.findAllById(memberIds).stream()
			.collect(Collectors.toMap(Member::getId, Function.identity()));
		Long organizationId = posting.getOrganization().getId();
		for (Long memberId : memberIds) {
			Member member = memberById.get(memberId);
			if (member == null || member.getLeftAt() != null
				|| !member.getOrganization().getId().equals(organizationId)) {
				throw new BusinessException(CommonErrorCode.INVALID_INPUT,
					InterviewInputReason.INTERVIEWER_NOT_MEMBER)
					.addContext("postingId", posting.getId())
					.addContext("memberId", memberId);
			}
		}
		if (!memberIds.isEmpty()) {
			List<Long> overlapping = interviewerRepository.findAllOverlapping(memberIds, interview.getId(),
					interview.getStartAt(), interview.getEndAt()).stream()
				.map(interviewer -> interviewer.getMember().getId())
				.distinct()
				.toList();
			if (!overlapping.isEmpty()) {
				throw new BusinessException(InterviewErrorCode.CONFLICT,
					InterviewConflictReason.INTERVIEWER_TIME_OVERLAP)
					.addContext("interviewId", interview.getId())
					.addContext("memberIds", overlapping);
			}
		}
		interviewerRepository.deleteAllByInterview(interview);
		for (Long memberId : memberIds) {
			interviewerRepository.save(Interviewer.create(interview, memberById.get(memberId)));
		}
	}

	// 지원자를 옮기거나 빼서 비게 된 칸은 면접관과 함께 지움. 화면에서는 그냥 빈 칸이 됨
	private void deleteEmptyInterviews(Posting posting) {
		List<Long> emptyIds = interviewRepository.findEmptyIdsByPosting(posting);
		if (emptyIds.isEmpty()) {
			return;
		}
		interviewerRepository.deleteAllByInterviewIdIn(emptyIds);
		interviewRepository.deleteAllInBulkByIdIn(emptyIds);
	}

	// 확정 뒤에는 수정 모드에서만 배정, 면접관, 면접실 열을 바꿀 수 있음
	private void requireEditable(Posting posting, Optional<InterviewTimetableState> state) {
		if (posting.getTimetableConfirmedAt() != null
			&& state.filter(InterviewTimetableState::isEditing).isEmpty()) {
			throw new BusinessException(InterviewErrorCode.CONFLICT, InterviewConflictReason.EDIT_MODE_REQUIRED)
				.addContext("postingId", posting.getId());
		}
	}

	// 면접실 열 개수. 공고 설정의 동시 진행 수, 운영진이 늘린 개수, 지금 쓰이는 열 번호 중 가장 큰 값
	private int trackCount(Posting posting, Optional<InterviewTimetableState> state,
		Optional<InterviewSetting> setting) {
		int fromSetting = setting.map(InterviewSetting::maxParallelInterviews).orElse(0);
		int fromState = state.map(InterviewTimetableState::getTrackCount).orElse(0);
		return Math.max(Math.max(fromSetting, fromState), interviewRepository.findMaxTrack(posting));
	}

	private Optional<InterviewTimetableState> findState(Posting posting) {
		return interviewTimetableStateRepository.findByPosting(posting);
	}

	// 빈 문자열이면 장소를 비움
	private String normalizeLocation(String location) {
		return location.isBlank() ? null : location.strip();
	}

	private Posting findPosting(Long postingId) {
		return postingRepository.findById(postingId)
			.orElseThrow(() -> new BusinessException(PostingErrorCode.NOT_FOUND).addContext("postingId", postingId));
	}

	// 같은 공고의 생성, 수정, 확정이 겹치지 않게 공고 행을 잠그고 시작
	private Posting lockOperablePosting(Long userId, Long postingId) {
		Posting posting = interviewRepository.findPostingForUpdate(postingId)
			.orElseThrow(() -> new BusinessException(PostingErrorCode.NOT_FOUND).addContext("postingId", postingId));
		requireOperator(userId, posting);
		requireHasInterview(posting);
		return posting;
	}

	private void requireHasInterview(Posting posting) {
		if (!posting.isHasInterview()) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT, InterviewInputReason.NO_INTERVIEW)
				.addContext("postingId", posting.getId());
		}
	}

	private InterviewSetting requireSetting(Posting posting) {
		return interviewSlotReader.setting(posting)
			.orElseThrow(() -> new BusinessException(CommonErrorCode.INVALID_INPUT,
				InterviewInputReason.INTERVIEW_SETTING_REQUIRED).addContext("postingId", posting.getId()));
	}

	private InterviewTimetable load(Posting posting) {
		Optional<InterviewSetting> setting = interviewSlotReader.setting(posting);
		Optional<InterviewTimetableState> state = findState(posting);
		List<Slot> slots = setting.map(found -> interviewSlotReader.slots(posting, found, range -> true))
			.orElseGet(List::of);
		List<Interview> interviews = interviewRepository.findAllByPostingOrderByStartAtAscTrackAscIdAsc(posting);
		Set<Long> assignedIds = new HashSet<>(interviewApplicantRepository.findApplicationIdsByPosting(posting));
		List<Application> unassigned = new ArrayList<>(passedApplications(posting).stream()
			.filter(application -> !assignedIds.contains(application.getId()))
			.sorted(SUBMITTED_ORDER)
			.toList());
		return new InterviewTimetable(posting, posting.getTimetableConfirmedAt(),
			state.filter(InterviewTimetableState::isEditing).isPresent(), trackCount(posting, state, setting),
			slots, interviews, unassigned, interviewRepository.findRecentLocations(posting));
	}

	// 배정 대상은 제출한 지원서 중 서류 합격만
	private List<Application> passedApplications(Posting posting) {
		return applicationRepository.findAllByPostingAndStatus(posting, ApplicationStatus.SUBMITTED).stream()
			.filter(application -> application.getDocResult() == DocResult.PASS)
			.toList();
	}

	private Candidate toCandidate(Application application) {
		List<LocalDateTime> availableTimes = application.getAvailableTimes();
		return new Candidate(application.getId(),
			application.getPosition() == null ? null : application.getPosition().getId(),
			application.getSubmittedAt(), availableTimes == null ? Set.of() : new HashSet<>(availableTimes));
	}

	private void requireOperator(Long userId, Posting posting) {
		Long organizationId = posting.getOrganization().getId();
		if (!organizationAccessChecker.isOperatorOf(userId, organizationId)) {
			throw new BusinessException(CommonErrorCode.FORBIDDEN)
				.addContext("userId", userId)
				.addContext("organizationId", organizationId);
		}
	}

}
