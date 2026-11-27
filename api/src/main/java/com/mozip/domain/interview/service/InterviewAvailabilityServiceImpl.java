package com.mozip.domain.interview.service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.domain.application.entity.Application;
import com.mozip.domain.application.exception.ApplicationErrorCode;
import com.mozip.domain.application.exception.ApplicationInputReason;
import com.mozip.domain.application.repository.ApplicationRepository;
import com.mozip.domain.interview.exception.InterviewConflictReason;
import com.mozip.domain.interview.exception.InterviewErrorCode;
import com.mozip.domain.interview.exception.InterviewInputReason;
import com.mozip.domain.interview.timetable.InterviewSetting;
import com.mozip.domain.interview.timetable.InterviewSlotCalculator;
import com.mozip.domain.interview.timetable.Slot;
import com.mozip.domain.interview.timetable.TimeRange;
import com.mozip.domain.posting.entity.InterviewTimeRange;
import com.mozip.domain.posting.entity.Position;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.repository.InterviewTimeRangeRepository;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;

import lombok.RequiredArgsConstructor;

// 면접 가능 시간은 Application.availableTimes에 저장함. 타임테이블 생성이 지원서와 같이 읽어서 따로 테이블을 두지 않음
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InterviewAvailabilityServiceImpl implements InterviewAvailabilityService {

	private final ApplicationRepository applicationRepository;
	private final InterviewTimeRangeRepository interviewTimeRangeRepository;

	@Override
	@Transactional
	public void save(Long applicationId, List<LocalDateTime> availableTimes) {
		Application application = findById(applicationId);
		replace(application, availableTimes == null ? List.of() : availableTimes);
	}

	@Override
	@Transactional
	public Application reselect(Long userId, Long applicationId, List<LocalDateTime> availableTimes) {
		Application application = findById(applicationId);
		if (!application.isOwnedBy(userId)) {
			throw new BusinessException(CommonErrorCode.FORBIDDEN)
				.addContext("userId", userId)
				.addContext("applicationId", applicationId);
		}
		Posting posting = application.getPosting();
		if (!posting.isHasInterview()) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT, InterviewInputReason.NO_INTERVIEW)
				.addContext("applicationId", applicationId);
		}
		// 제출할 때 필수였던 값이라 재선택으로 비울 수 없음
		if (availableTimes == null || availableTimes.isEmpty()) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT, InterviewInputReason.AVAILABLE_TIMES_REQUIRED)
				.addContext("applicationId", applicationId);
		}
		// 확정된 타임테이블은 지원자에게 이미 안내된 일정이라 가능 시간이 바뀌면 배정과 어긋남
		if (posting.getTimetableConfirmedAt() != null) {
			throw new BusinessException(InterviewErrorCode.CONFLICT, InterviewConflictReason.TIMETABLE_CONFIRMED)
				.addContext("applicationId", applicationId)
				.addContext("postingId", posting.getId());
		}
		replace(application, availableTimes);
		return application;
	}

	private void replace(Application application, List<LocalDateTime> availableTimes) {
		// 같은 시각을 두 번 보내도 한 번만 저장하고 시간순으로 맞춤
		List<LocalDateTime> times = availableTimes.stream().distinct().sorted().toList();
		validateOpen(application.getPosting(), application.getPosition(), times);
		application.changeAvailableTimes(times);
	}

	// 운영진이 연 시간대(InterviewTimeRange)를 블록으로 잘라서, 고른 시간이 열려 있는 블록의 시작 시각인지 확인
	private void validateOpen(Posting posting, Position position, List<LocalDateTime> availableTimes) {
		if (availableTimes.isEmpty()) {
			return;
		}
		Set<LocalDateTime> openStartTimes = openSlotStartTimes(posting, position);
		for (LocalDateTime time : availableTimes) {
			if (!openStartTimes.contains(time)) {
				throw new BusinessException(CommonErrorCode.INVALID_INPUT,
					ApplicationInputReason.AVAILABLE_TIME_NOT_OPEN)
					.addContext("postingId", posting.getId())
					.addContext("availableTime", time);
			}
		}
	}

	// 포지션 전용 시간대는 그 포지션 지원자에게만 열림
	private Set<LocalDateTime> openSlotStartTimes(Posting posting, Position position) {
		if (posting.getInterviewMinutes() == null || posting.getBreakMinutes() == null
			|| posting.getMaxApplicantsPerInterview() == null || posting.getMaxParallelInterviews() == null
			|| posting.getAssignRule() == null) {
			return Set.of();
		}
		List<TimeRange> ranges = interviewTimeRangeRepository.findAllByPostingOrderByDateAscStartTimeAsc(posting)
			.stream()
			.filter(range -> range.getPosition() == null
				|| (position != null && range.getPosition().getId().equals(position.getId())))
			.map(this::toTimeRange)
			.toList();
		if (ranges.isEmpty()) {
			return Set.of();
		}
		InterviewSetting setting = new InterviewSetting(posting.getInterviewMinutes(), posting.getBreakMinutes(),
			posting.getMaxApplicantsPerInterview(), posting.getMaxParallelInterviews(), posting.getAssignRule());
		return InterviewSlotCalculator.calculate(ranges, setting).stream()
			.filter(slot -> !slot.closed())
			.map(Slot::startAt)
			.collect(Collectors.toSet());
	}

	private TimeRange toTimeRange(InterviewTimeRange range) {
		List<LocalTime> closedSlots = range.getClosedSlots();
		return new TimeRange(range.getDate(), range.getStartTime(), range.getEndTime(),
			closedSlots == null ? Set.of() : Set.copyOf(closedSlots));
	}

	private Application findById(Long applicationId) {
		return applicationRepository.findById(applicationId)
			.orElseThrow(() -> new BusinessException(ApplicationErrorCode.NOT_FOUND)
				.addContext("applicationId", applicationId));
	}

}
