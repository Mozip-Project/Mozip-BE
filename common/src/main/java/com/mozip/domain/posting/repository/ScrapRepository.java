package com.mozip.domain.posting.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.entity.Scrap;
import com.mozip.domain.user.entity.User;

public interface ScrapRepository extends JpaRepository<Scrap, Long> {

	Optional<Scrap> findByUserAndPosting(User user, Posting posting);

	long countByPosting(Posting posting);

	// 내 스크랩 목록. 마감된 공고도 포함해서 보여주므로 상태로 거르지 않음
	@Query("SELECT s.posting FROM Scrap s WHERE s.user = :user ORDER BY s.createdAt DESC")
	Page<Posting> findPostingsByUser(@Param("user") User user, Pageable pageable);

}
