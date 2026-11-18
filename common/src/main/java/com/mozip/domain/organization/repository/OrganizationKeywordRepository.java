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

public interface OrganizationKeywordRepository extends JpaRepository<OrganizationKeyword, Long> {

	// GraphQL Organization.keywords 배치 조회
	List<OrganizationKeyword> findAllByOrganizationInOrderById(Collection<Organization> organizations);

	// 전체 교체 시 JPQL delete로 실행하여 같은 키워드를 다시 넣는 경우 unique index에 걸리지 않도록
	@Modifying
	@Query("delete from OrganizationKeyword k where k.organization = :organization")
	void deleteAllByOrganization(@Param("organization") Organization organization);

	// 자동완성, 전 단체 키워드 중 접두 일치를 많이 쓰인 순으로. pattern은 서비스에서 !로 이스케이프한 뒤 %를 붙여 전달
	@Query("select k.keyword from OrganizationKeyword k where k.keyword like :pattern escape '!' "
		+ "group by k.keyword order by count(k) desc, k.keyword asc")
	List<String> findKeywordsByPrefix(@Param("pattern") String pattern, Pageable pageable);

}
