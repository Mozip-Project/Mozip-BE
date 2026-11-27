package com.mozip.domain.interview.service;

import java.time.LocalDateTime;
import java.util.List;

import com.mozip.domain.application.entity.Application;

// B 소유 인터페이스. 면접 가능 시간의 검증과 저장을 맡음 (결정 005)
public interface InterviewAvailabilityService {

	// A의 saveApplicationDraft가 부름. 열려 있는 블록의 시작 시각인지 확인하고 기존 값을 통째로 교체
	void save(Long applicationId, List<LocalDateTime> availableTimes);

	// 지원자 본인이 제출 뒤에도 다시 고름. 타임테이블이 확정되면 못 바꿈
	Application reselect(Long userId, Long applicationId, List<LocalDateTime> availableTimes);

}
