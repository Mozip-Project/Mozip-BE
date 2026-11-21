package com.mozip.domain.posting.repository;

import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mozip.domain.organization.entity.Team;
import com.mozip.domain.posting.entity.Position;

public interface PositionRepository extends JpaRepository<Position, Long> {

	// 팀 삭제 전 공고 포지션이 참조하는지 확인 (TEAM_409)
	boolean existsByTeamIn(Collection<Team> teams);

}
