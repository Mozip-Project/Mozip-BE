package com.mozip.domain.interview.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import com.mozip.domain.interview.entity.Interview;
import com.mozip.domain.interview.entity.InterviewApplicant;
import com.mozip.domain.interview.entity.Interviewer;
import com.mozip.domain.interview.exception.InterviewConflictReason;
import com.mozip.domain.interview.exception.InterviewErrorCode;
import com.mozip.domain.interview.exception.InterviewInputReason;
import com.mozip.domain.interview.repository.InterviewApplicantRepository;
import com.mozip.domain.interview.repository.InterviewRepository;
import com.mozip.domain.interview.repository.InterviewerRepository;
import com.mozip.domain.interview.timetable.AssignedInterview;
import com.mozip.domain.interview.timetable.Candidate;
import com.mozip.domain.interview.timetable.InterviewSetting;
import com.mozip.domain.interview.timetable.Slot;
import com.mozip.domain.interview.timetable.TimetableGenerator;
import com.mozip.domain.interview.timetable.TimetableResult;
import com.mozip.domain.notification.entity.NotificationType;
import com.mozip.domain.notification.service.NotificationService;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.service.OrganizationAccessChecker;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.exception.PostingErrorCode;
import com.mozip.domain.posting.repository.PostingRepository;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;

import lombok.RequiredArgsConstructor;

// 운영진의 면접 타임테이블 조회, 자동 생성, 칸 수정, 확정
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
	private final InterviewSlotReader interviewSlotReader;
	private final OrganizationAccessChecker organizationAccessChecker;
	private final MemberRepository memberRepository;
	private final NotificationService notificationService;
	private final Clock clock;

	public InterviewTimetable find(Long userId, Long postingId) {
		Posting posting = postingRepository.findById(postingId)
			.orElseThrow(() -> new BusinessException(PostingErrorCode.NOT_FOUND).addContext("postingId", postingId));
		requireOperator(userId, posting);
		return load(posting);
	}

	// 서류 합격자를 면접 가능 시간에 맞춰 배정. 다시 부르면 기존 배정(면접관, 장소 포함)을 지우고 새로 만듦
	@Transactional
	public InterviewTimetable generate(Long userId, Long postingId) {
		Posting posting = lockOperablePosting(userId, postingId);
		// 확정된 타임테이블은 지원자에게 이미 안내된 일정이라 통째로 다시 만들 수 없음
		if (posting.getTimetableConfirmedAt() != null) {
			throw new BusinessException(InterviewErrorCode.CONFLICT, InterviewConflictReason.TIMETABLE_CONFIRMED)
				.addContext("postingId", postingId);
		}
		InterviewSetting setting = requireSetting(posting);

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

	// 칸 하나를 추가하거나 고침. 확정 뒤에도 고칠 수 있음
	// 자동 생성과 달리 직접 배정은 지원자가 고른 시각, 포지션, 정원을 따지지 않음
	@Transactional
	public InterviewTimetable update(Long userId, Long postingId, InterviewInput input) {
		Posting posting = lockOperablePosting(userId, postingId);
		Interview interview = input.id() == null
			? addInterview(posting, input.startAt())
			: findInterview(posting, input.id());
		if (input.location() != null) {
			// 빈 문자열이면 장소를 비움
			interview.changeLocation(input.location().isBlank() ? null : input.location().strip());
		}
		if (input.applicationIds() != null) {
			replaceApplicants(posting, interview, input.applicationIds().stream().distinct().toList());
		}
		if (input.interviewerMemberIds() != null) {
			replaceInterviewers(posting, interview, input.interviewerMemberIds().stream().distinct().toList());
		}
		// 장소만 바꾼 경우에도 최근 장소 조회에 바로 보이게 함
		interviewRepository.flush();
		return load(posting);
	}

	// 칸을 지우면 거기 있던 지원자는 미배정으로 돌아감
	@Transactional
	public InterviewTimetable delete(Long userId, Long interviewId) {
		Long postingId = interviewRepository.findById(interviewId)
			.map(interview -> interview.getPosting().getId())
			.orElseThrow(() -> new BusinessException(InterviewErrorCode.NOT_FOUND)
				.addContext("interviewId", interviewId));
		Posting posting = lockOperablePosting(userId, postingId);
		Interview interview = findInterview(posting, interviewId);
		interviewerRepository.deleteAllByInterview(interview);
		interviewApplicantRepository.deleteAllByInterview(interview);
		interviewRepository.deleteInBulkById(interviewId);
		// 위 삭제가 영속성 컨텍스트를 비워서 공고를 다시 읽음
		return load(postingRepository.findById(postingId).orElseThrow());
	}

	// 미배정 지원자가 남아 있어도 확정할 수 있음. 확정하면 면접 가능 시간 재선택과 자동 생성이 잠김
	@Transactional
	public InterviewTimetable confirm(Long userId, Long postingId) {
		Posting posting = postingRepository.findById(postingId)
			.orElseThrow(() -> new BusinessException(PostingErrorCode.NOT_FOUND).addContext("postingId", postingId));
		requireOperator(userId, posting);
		requireHasInterview(posting);
		Long organizationId = posting.getOrganization().getId();
		String title = notificationTitle(posting);
		List<Long> applicantUserIds = interviewApplicantRepository.findUserIdsByPosting(posting);

		if (applicantUserIds.isEmpty()) {
			throw new BusinessException(InterviewErrorCode.CONFLICT, InterviewConflictReason.NO_ASSIGNED_APPLICANT)
				.addContext("postingId", postingId);
		}
		// 확정 전일 때만 채우는 조건부 UPDATE라 동시에 두 번 눌러도 알림은 한 번만 나감
		if (interviewRepository.confirmTimetable(postingId, LocalDateTime.now(clock)) == 0) {
			throw new BusinessException(InterviewErrorCode.CONFLICT, InterviewConflictReason.ALREADY_CONFIRMED)
				.addContext("postingId", postingId);
		}
		// TODO(B 결과 안내): 면접 일정 화면 경로가 정해지면 linkUrl을 채움
		for (Long applicantUserId : applicantUserIds) {
			notificationService.send(applicantUserId, NotificationType.POSTING_NOTICE, organizationId, title, null);
		}
		// 위 UPDATE가 영속성 컨텍스트를 비워서 확정 시각이 들어간 공고를 다시 읽음
		return load(postingRepository.findById(postingId).orElseThrow());
	}

	private Interview addInterview(Posting posting, LocalDateTime startAt) {
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
		// 같은 시각에 이미 있는 면접실 열 다음 번호로 추가
		int track = interviewRepository.findMaxTrack(posting, startAt) + 1;
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
			List<Long> overlapping = interviewerRepository.findOverlappingMemberIds(memberIds, interview.getId(),
				interview.getStartAt(), interview.getEndAt());
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

	// 같은 공고의 생성, 수정이 겹치지 않게 공고 행을 잠그고 시작
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

	// 알림 제목 한도가 200자라 공고 제목은 앞 150자만 씀
	private String notificationTitle(Posting posting) {
		String postingTitle = posting.getTitle() == null ? "" : posting.getTitle();
		if (postingTitle.length() > 150) {
			postingTitle = postingTitle.substring(0, 150);
		}
		return (postingTitle + " 면접 일정이 확정됐어요").strip();
	}

	private InterviewTimetable load(Posting posting) {
		List<Slot> slots = interviewSlotReader.setting(posting)
			.map(setting -> interviewSlotReader.slots(posting, setting, range -> true))
			.orElse(List.of());
		List<Interview> interviews = interviewRepository.findAllByPostingOrderByStartAtAscTrackAscIdAsc(posting);
		Set<Long> assignedIds = new HashSet<>(interviewApplicantRepository.findApplicationIdsByPosting(posting));
		List<Application> unassigned = passedApplications(posting).stream()
			.filter(application -> !assignedIds.contains(application.getId()))
			.sorted(SUBMITTED_ORDER)
			.toList();
		return new InterviewTimetable(posting, posting.getTimetableConfirmedAt(), slots, interviews, unassigned,
			interviewRepository.findRecentLocations(posting));
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
