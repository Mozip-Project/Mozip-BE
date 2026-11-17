package com.mozip.domain.organization.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mozip.domain.organization.entity.Organization;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

}
