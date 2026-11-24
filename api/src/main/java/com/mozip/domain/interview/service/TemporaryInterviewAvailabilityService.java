package com.mozip.domain.interview.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

// TODO(B 면접): B가 면접 타임테이블 블록 계산·배정을 구현하면 실제 구현으로 교체. 지금은 로그만 남김 (결정 005)
@Slf4j
@Service
public class TemporaryInterviewAvailabilityService implements InterviewAvailabilityService {

	@Override
	public void save(Long applicationId, List<LocalDateTime> availableTimes) {
		log.info("[application] applicationId={} 면접 가능 시간 {}건 저장(임시 구현, 배정 미반영)", applicationId,
			availableTimes == null ? 0 : availableTimes.size());
	}

}
