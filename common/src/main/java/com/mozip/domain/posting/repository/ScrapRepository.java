package com.mozip.domain.posting.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.entity.Scrap;

public interface ScrapRepository extends JpaRepository<Scrap, Long> {

	List<Scrap> findAllByPostingIn(Collection<Posting> postings);

	List<Scrap> findAllByUser_IdAndPostingIn(Long userId, Collection<Posting> postings);

}
