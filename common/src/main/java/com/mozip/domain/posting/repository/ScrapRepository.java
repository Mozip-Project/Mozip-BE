package com.mozip.domain.posting.repository;

import java.util.Collection;
import java.util.List;

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

	long countByPosting(Posting posting);

	// 삭제 건수로 직접 분기(toggle)해서 조회 없이 "있었는지"를 알 수 있고, 동시에 같은 (user, posting)로
	// toggle이 들어와도 엔티티를 읽어 지우는 방식과 달리 늦게 도착한 쪽이 0건을 지우고 조용히 끝남
	// (엔티티 삭제는 Hibernate가 1건 삭제를 기대해서 이미 지워진 행이면 StaleObjectStateException이 남)
	@Modifying
	@Query("DELETE FROM Scrap s WHERE s.user.id = :userId AND s.posting.id = :postingId")
	int deleteByUserIdAndPostingId(@Param("userId") Long userId, @Param("postingId") Long postingId);

	// 내 스크랩 목록. 마감된 공고도 포함해서 보여주므로 상태로 거르지 않음
	@Query("SELECT s.posting FROM Scrap s WHERE s.user = :user ORDER BY s.createdAt DESC, s.id DESC")
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

	// 공고 카드 목록에서 스크랩 수/내 스크랩 여부를 묶어 조회할 때 씀 (PostingService)
	List<Scrap> findAllByPostingIn(Collection<Posting> postings);

	List<Scrap> findAllByUser_IdAndPostingIn(Long userId, Collection<Posting> postings);

	// 공고 삭제 시 같이 지움 (PostingService.delete). FK(fk_scraps_posting_id)만 있고 cascade는 없음
	void deleteAllByPosting(Posting posting);

}
