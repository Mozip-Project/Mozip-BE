package com.mozip.domain.posting.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mozip.domain.organization.entity.Team;
import com.mozip.domain.posting.entity.Position;
import com.mozip.domain.posting.entity.Posting;

public interface PositionRepository extends JpaRepository<Position, Long> {

	// 팀 삭제 전 공고 포지션이 참조하는지 확인 (TEAM_409)
	boolean existsByTeamIn(Collection<Team> teams);
	List<Position> findAllByPostingOrderBySortOrder(Posting posting);

	List<Position> findAllByPostingInOrderBySortOrder(Collection<Posting> postings);

	void deleteAllByPosting(Posting posting);

}
