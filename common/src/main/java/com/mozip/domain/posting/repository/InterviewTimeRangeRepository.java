package com.mozip.domain.posting.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mozip.domain.posting.entity.InterviewTimeRange;
import com.mozip.domain.posting.entity.Posting;

public interface InterviewTimeRangeRepository extends JpaRepository<InterviewTimeRange, Long> {

	List<InterviewTimeRange> findAllByPostingOrderByDateAscStartTimeAsc(Posting posting);

	List<InterviewTimeRange> findAllByPostingInOrderByDateAscStartTimeAsc(Collection<Posting> postings);

	void deleteAllByPosting(Posting posting);

}
