package com.mozip.domain.interview.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mozip.domain.interview.entity.Interviewer;

public interface InterviewerRepository extends JpaRepository<Interviewer, Long> {

}
