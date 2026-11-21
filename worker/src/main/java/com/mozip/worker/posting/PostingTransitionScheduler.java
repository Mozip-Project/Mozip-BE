package com.mozip.worker.posting;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.domain.posting.repository.PostingRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 모집 마감 자동 전환. 조건부 UPDATE(PostingRepository)로 처리해서 worker 파드가 여러 개여도
// 행 하나는 한 번만 바뀌므로 ShedLock 같은 분산 락은 안 씀
@Slf4j
@Component
@RequiredArgsConstructor
public class PostingTransitionScheduler {

	private final PostingRepository postingRepository;

	@Scheduled(cron = "0 * * * * *")
	@Transactional
	public void closeRecruiting() {
		int closed = postingRepository.closeRecruitingScheduled(LocalDateTime.now());
		if (closed > 0) {
			log.info("[posting] 모집 마감 {}건 전환", closed);
		}
	}

}
