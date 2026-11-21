package com.mozip.domain.posting.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mozip.domain.posting.entity.Position;
import com.mozip.domain.posting.entity.Posting;

public interface PositionRepository extends JpaRepository<Position, Long> {

	List<Position> findAllByPostingOrderBySortOrder(Posting posting);

	List<Position> findAllByPostingInOrderBySortOrder(Collection<Posting> postings);

	void deleteAllByPosting(Posting posting);

}
