package com.mozip.domain.posting.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;

import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.entity.PostingStatus;

import jakarta.persistence.LockModeType;

public interface PostingRepository extends JpaRepository<Posting, Long>, JpaSpecificationExecutor<Posting> {

	// 상세 조회용. school까지 같이 당겨와 open-in-view=false에서도 organization.school을 바로 쓸 수 있게 함
	@Query("""
		SELECT p FROM Posting p
		JOIN FETCH p.organization o
		LEFT JOIN FETCH o.school
		WHERE p.id = :id
		""")
	Optional<Posting> findByIdWithOrganization(@Param("id") Long id);

	List<Posting> findAllByOrganizationAndStatusInOrderByCreatedAtDesc(Organization organization,
		List<PostingStatus> statuses);

	// 모집 마감 스케줄러. 조건부 UPDATE라 worker 파드가 여러 개여도 행 하나는 딱 한 번만 바뀜
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
		UPDATE Posting p SET p.status = com.mozip.domain.posting.entity.PostingStatus.DOC_REVIEW
		WHERE p.status = com.mozip.domain.posting.entity.PostingStatus.RECRUITING
			AND p.recruitEndAt <= :now
		""")
	int closeRecruitingScheduled(@Param("now") LocalDateTime now);

	// 서류 결과 확정 (B). 조건부 UPDATE라 동시에 눌러도 한 번만 1을 돌려주고 나머지는 0
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
		UPDATE Posting p SET p.docResultConfirmedAt = :now
		WHERE p.id = :id AND p.docResultConfirmedAt IS NULL
		""")
	int confirmDocResult(@Param("id") Long id, @Param("now") LocalDateTime now);

	// 서류 결과 지정과 확정 (B). 두 요청이 엇갈리지 않게 공고 행을 잠그고 읽음
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT p FROM Posting p JOIN FETCH p.organization WHERE p.id = :id")
	Optional<Posting> findByIdWithOrganizationForUpdate(@Param("id") Long id);

}
