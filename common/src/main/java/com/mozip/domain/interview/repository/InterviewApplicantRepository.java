package com.mozip.domain.interview.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mozip.domain.interview.entity.Interview;
import com.mozip.domain.interview.entity.InterviewApplicant;
import com.mozip.domain.posting.entity.Posting;

public interface InterviewApplicantRepository extends JpaRepository<InterviewApplicant, Long> {

	@Query("select ia from InterviewApplicant ia join fetch ia.application "
		+ "where ia.interview in :interviews order by ia.id")
	List<InterviewApplicant> findAllWithApplicationByInterviewIn(
		@Param("interviews") Collection<Interview> interviews);

	@Query("select ia.application.id from InterviewApplicant ia where ia.interview.posting = :posting")
	List<Long> findApplicationIdsByPosting(@Param("posting") Posting posting);

	@Modifying(flushAutomatically = true)
	@Query("delete from InterviewApplicant ia "
		+ "where ia.interview.id in (select i.id from Interview i where i.posting = :posting)")
	void deleteAllByPosting(@Param("posting") Posting posting);

}
