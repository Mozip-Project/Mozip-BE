package com.mozip.domain.organization.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.Team;

public interface TeamRepository extends JpaRepository<Team, Long> {

	// GraphQL Organization.teams 배치 조회, 팀 설정 화면 순서대로
	List<Team> findAllByOrganizationInOrderBySortOrderAscIdAsc(Collection<Organization> organizations);

	List<Team> findAllByOrganizationIdOrderBySortOrderAscIdAsc(Long organizationId);

	Optional<Team> findByIdAndOrganizationId(Long id, Long organizationId);

}
