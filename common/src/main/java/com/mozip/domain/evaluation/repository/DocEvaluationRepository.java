package com.mozip.domain.evaluation.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mozip.domain.evaluation.entity.DocEvaluation;

public interface DocEvaluationRepository extends JpaRepository<DocEvaluation, Long> {

}
