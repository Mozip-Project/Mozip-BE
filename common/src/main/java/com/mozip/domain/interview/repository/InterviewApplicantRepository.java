package com.mozip.domain.interview.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mozip.domain.interview.entity.InterviewApplicant;

public interface InterviewApplicantRepository extends JpaRepository<InterviewApplicant, Long> {

}
