package com.mozip.domain.organization.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.OrganizationKeyword;
import com.mozip.global.common.ReviewStatus;

public interface OrganizationKeywordRepository extends JpaRepository<OrganizationKeyword, Long> {

	// GraphQL Organization.keywords 배치 조회
	List<OrganizationKeyword> findAllByOrganizationInOrderById(Collection<Organization> organizations);

	// 전체 교체용 일괄 삭제
	@Modifying
	@Query("delete from OrganizationKeyword k where k.organization = :organization")
	void deleteAllByOrganization(@Param("organization") Organization organization);

	// 자동완성, 접두 일치 키워드를 많이 쓰인 순으로 (pattern은 !로 이스케이프하고 %를 붙여 전달)
	@Query("select k.keyword from OrganizationKeyword k where k.keyword like :pattern escape '!' "
		+ "and k.organization.reviewStatus = :status group by k.keyword order by count(k) desc, k.keyword asc")
	List<String> findKeywordsByPrefix(@Param("pattern") String pattern, @Param("status") ReviewStatus status,
		Pageable pageable);

}
