package com.mozip.domain.interview.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mozip.domain.interview.entity.InterviewTimetableState;
import com.mozip.domain.posting.entity.Posting;

public interface InterviewTimetableStateRepository extends JpaRepository<InterviewTimetableState, Long> {

	Optional<InterviewTimetableState> findByPosting(Posting posting);

}
