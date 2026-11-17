package com.mozip.domain.evaluation.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mozip.domain.evaluation.entity.Reviewer;

public interface ReviewerRepository extends JpaRepository<Reviewer, Long> {

}
