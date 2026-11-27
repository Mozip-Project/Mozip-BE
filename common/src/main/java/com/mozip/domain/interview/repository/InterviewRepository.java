package com.mozip.domain.interview.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mozip.domain.interview.entity.Interview;
import com.mozip.domain.posting.entity.Posting;

import jakarta.persistence.LockModeType;

public interface InterviewRepository extends JpaRepository<Interview, Long> {

	List<Interview> findAllByPostingOrderByStartAtAscTrackAscIdAsc(Posting posting);

	// 이 모집에서 쓴 장소, 최근에 쓴 순
	@Query("select i.location from Interview i where i.posting = :posting and i.location is not null "
		+ "group by i.location order by max(i.updatedAt) desc")
	List<String> findRecentLocations(@Param("posting") Posting posting);

	// 같은 공고의 타임테이블 생성, 수정, 확정이 겹치지 않게 공고 행을 잠금
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Posting p where p.id = :postingId")
	Optional<Posting> findPostingForUpdate(@Param("postingId") Long postingId);

	@Modifying(flushAutomatically = true)
	@Query("delete from Interview i where i.posting = :posting")
	void deleteAllByPosting(@Param("posting") Posting posting);

}
