package com.mozip.domain.posting.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
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

}
