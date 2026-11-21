package com.mozip.domain.organization.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.OrganizationLink;

public interface OrganizationLinkRepository extends JpaRepository<OrganizationLink, Long> {

	// GraphQL Organization.links 배치 조회
	List<OrganizationLink> findAllByOrganizationInOrderById(Collection<Organization> organizations);

	// 전체 교체용 일괄 삭제
	@Modifying
	@Query("delete from OrganizationLink l where l.organization = :organization")
	void deleteAllByOrganization(@Param("organization") Organization organization);

}
