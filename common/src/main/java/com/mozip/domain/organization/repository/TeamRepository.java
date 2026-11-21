package com.mozip.domain.organization.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.Team;

public interface TeamRepository extends JpaRepository<Team, Long> {

	// GraphQL Organization.teams 배치 조회, 단체와 학교를 함께 조회
	@Query("select t from Team t join fetch t.organization o left join fetch o.school "
		+ "where t.organization in :organizations order by t.id")
	List<Team> findAllByOrganizationInOrderById(@Param("organizations") Collection<Organization> organizations);

}
