package com.mozip.domain.organization.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mozip.domain.organization.entity.Team;

public interface TeamRepository extends JpaRepository<Team, Long> {

}
