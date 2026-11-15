package com.mozip.domain.school.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mozip.domain.school.entity.School;

public interface SchoolRepository extends JpaRepository<School, Long> {

}
