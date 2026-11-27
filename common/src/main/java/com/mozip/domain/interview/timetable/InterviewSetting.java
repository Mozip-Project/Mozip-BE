package com.mozip.domain.interview.timetable;

// 공고의 면접 설정. 블록 하나 = 면접 1회 시간 + 쉬는 시간
public record InterviewSetting(int interviewMinutes, int breakMinutes, int maxApplicantsPerInterview,
	int maxParallelInterviews) {

	public InterviewSetting {
		if (interviewMinutes <= 0) {
			throw new IllegalArgumentException("면접 1회 시간은 1분 이상이어야 합니다.");
		}
		if (breakMinutes < 0) {
			throw new IllegalArgumentException("쉬는 시간은 0분 이상이어야 합니다.");
		}
		if (maxApplicantsPerInterview <= 0) {
			throw new IllegalArgumentException("면접 1회 지원자 수는 1명 이상이어야 합니다.");
		}
		if (maxParallelInterviews <= 0) {
			throw new IllegalArgumentException("동시 진행 면접 수는 1개 이상이어야 합니다.");
		}
	}

	public int blockMinutes() {
		return interviewMinutes + breakMinutes;
	}

}
