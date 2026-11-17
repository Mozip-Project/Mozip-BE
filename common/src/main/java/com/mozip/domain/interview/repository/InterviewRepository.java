package com.mozip.domain.interview.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mozip.domain.interview.entity.Interview;

public interface InterviewRepository extends JpaRepository<Interview, Long> {

}
