package com.mozip.domain.posting.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mozip.domain.posting.entity.Posting;

public interface PostingRepository extends JpaRepository<Posting, Long> {

}
