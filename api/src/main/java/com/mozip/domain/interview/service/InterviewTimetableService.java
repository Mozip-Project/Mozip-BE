package com.mozip.domain.interview.service;

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
import com.mozip.domain.interview.dto.InterviewTimetable;
import com.mozip.domain.interview.entity.Interview;
import com.mozip.domain.interview.entity.InterviewApplicant;
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
import com.mozip.domain.organization.service.OrganizationAccessChecker;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.exception.PostingErrorCode;
import com.mozip.domain.posting.repository.PostingRepository;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;

import lombok.RequiredArgsConstructor;

// 운영진의 면접 타임테이블 조회와 자동 생성
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

	public InterviewTimetable find(Long userId, Long postingId) {
		Posting posting = postingRepository.findById(postingId)
			.orElseThrow(() -> new BusinessException(PostingErrorCode.NOT_FOUND).addContext("postingId", postingId));
		requireOperator(userId, posting);
		return load(posting);
	}

	// 서류 합격자를 면접 가능 시간에 맞춰 배정. 다시 부르면 기존 배정(면접관, 장소 포함)을 지우고 새로 만듦
	@Transactional
	public InterviewTimetable generate(Long userId, Long postingId) {
		Posting posting = interviewRepository.findPostingForUpdate(postingId)
			.orElseThrow(() -> new BusinessException(PostingErrorCode.NOT_FOUND).addContext("postingId", postingId));
		requireOperator(userId, posting);
		if (!posting.isHasInterview()) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT, InterviewInputReason.NO_INTERVIEW)
				.addContext("postingId", postingId);
		}
		// 확정된 타임테이블은 지원자에게 이미 안내된 일정이라 통째로 다시 만들 수 없음
		if (posting.getTimetableConfirmedAt() != null) {
			throw new BusinessException(InterviewErrorCode.CONFLICT, InterviewConflictReason.TIMETABLE_CONFIRMED)
				.addContext("postingId", postingId);
		}
		InterviewSetting setting = interviewSlotReader.setting(posting)
			.orElseThrow(() -> new BusinessException(CommonErrorCode.INVALID_INPUT,
				InterviewInputReason.INTERVIEW_SETTING_REQUIRED).addContext("postingId", postingId));

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
