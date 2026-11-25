package com.mozip.domain.interview.service;

import java.time.LocalDateTime;
import java.util.List;

// B 소유 인터페이스. B가 면접 타임테이블 배정을 구현하면 TemporaryInterviewAvailabilityService를 실제 구현으로 교체함 (결정 005)
public interface InterviewAvailabilityService {

	void save(Long applicationId, List<LocalDateTime> availableTimes);

}
