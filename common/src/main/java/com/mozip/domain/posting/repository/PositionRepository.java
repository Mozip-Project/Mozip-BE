package com.mozip.domain.posting.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mozip.domain.posting.entity.Position;

public interface PositionRepository extends JpaRepository<Position, Long> {

}
