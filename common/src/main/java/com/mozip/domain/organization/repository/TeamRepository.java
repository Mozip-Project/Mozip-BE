package com.mozip.domain.organization.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.Team;

public interface TeamRepository extends JpaRepository<Team, Long> {

	// GraphQL Organization.teams 배치 조회
	List<Team> findAllByOrganizationInOrderById(Collection<Organization> organizations);

}
