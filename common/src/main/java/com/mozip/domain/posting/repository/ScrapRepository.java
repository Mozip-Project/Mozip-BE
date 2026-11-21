package com.mozip.domain.posting.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

	// 동시에 같은 (user, posting)로 toggle이 들어와도 예외 없이 끝나도록 DB가 충돌을 삼키게 함.
	// try/catch로 잡으면 같은 트랜잭션에서 뒤이은 조회(countByPosting)가 전부 실패함 (Postgres는 한 문장이라도
	// 실패하면 트랜잭션 전체를 abort 상태로 만듦). now()는 트랜잭션 시작 시각으로 고정이라 한 트랜잭션에서 여러 번
	// 스크랩하면 created_at이 전부 같아져 최근 순 정렬이 깨짐 -> clock_timestamp()로 실제 실행 시각을 씀
	@Modifying
	@Query(value = "INSERT INTO scraps (user_id, posting_id, created_at, updated_at) "
		+ "VALUES (:userId, :postingId, clock_timestamp(), clock_timestamp()) "
		+ "ON CONFLICT (user_id, posting_id) DO NOTHING",
		nativeQuery = true)
	void insertIfAbsent(@Param("userId") Long userId, @Param("postingId") Long postingId);

}
