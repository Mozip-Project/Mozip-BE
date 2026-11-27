package com.mozip.domain.posting.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;

import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.entity.PostingStatus;

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

	// 스크랩, 신고처럼 지원자 쪽에서 쓰는 조회. DRAFT는 아직 게시 전이라 없는 공고와 같게 취급
	@Query("SELECT p FROM Posting p WHERE p.id = :id AND p.status <> com.mozip.domain.posting.entity.PostingStatus.DRAFT")
	Optional<Posting> findPublishedById(@Param("id") Long id);

	// 모집 마감 스케줄러. 조건부 UPDATE라 worker 파드가 여러 개여도 행 하나는 딱 한 번만 바뀜
	// bulk update는 AuditingEntityListener를 안 타서 updatedAt을 직접 같이 SET함
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
		UPDATE Posting p SET p.status = com.mozip.domain.posting.entity.PostingStatus.DOC_REVIEW, p.updatedAt = :now
		WHERE p.status = com.mozip.domain.posting.entity.PostingStatus.RECRUITING
			AND p.recruitEndAt <= :now
		""")
	int closeRecruitingScheduled(@Param("now") LocalDateTime now);

}
