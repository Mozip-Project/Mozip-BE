package com.mozip.domain.organization.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mozip.domain.organization.entity.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {

	boolean existsByOrganizationIdAndUserId(Long organizationId, Long userId);

	List<Member> findAllByUserId(Long userId);

}
