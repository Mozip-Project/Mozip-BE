package com.mozip.domain.organization.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.Team;

public interface TeamRepository extends JpaRepository<Team, Long> {

	// GraphQL Organization.teams 배치 조회 (팀 설정 화면 순서), 단체와 학교를 함께 조회
	@Query("select t from Team t join fetch t.organization o left join fetch o.school "
		+ "where t.organization in :organizations order by t.sortOrder, t.id")
	List<Team> findAllByOrganizationInOrderBySortOrderAscIdAsc(@Param("organizations") Collection<Organization> organizations);

	List<Team> findAllByOrganizationIdOrderBySortOrderAscIdAsc(Long organizationId);

	Optional<Team> findByIdAndOrganizationId(Long id, Long organizationId);

}
