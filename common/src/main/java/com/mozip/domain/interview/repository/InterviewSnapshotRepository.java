package com.mozip.domain.interview.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mozip.domain.interview.entity.InterviewSnapshot;
import com.mozip.domain.posting.entity.Posting;

public interface InterviewSnapshotRepository extends JpaRepository<InterviewSnapshot, Long> {

	List<InterviewSnapshot> findAllByPostingOrderByStartAtAscTrackAscIdAsc(Posting posting);

	@Modifying(flushAutomatically = true)
	@Query("delete from InterviewSnapshot s where s.posting = :posting")
	void deleteAllByPosting(@Param("posting") Posting posting);

}
