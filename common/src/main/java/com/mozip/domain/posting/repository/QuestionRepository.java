package com.mozip.domain.posting.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mozip.domain.posting.entity.Question;

public interface QuestionRepository extends JpaRepository<Question, Long> {

}
