package com.mozip.domain.organization.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mozip.domain.organization.entity.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {

	// TODO(B 단체): OrganizationAccessChecker 임시 구현(TemporaryOrganizationAccessChecker)에서 씀
	boolean existsByOrganizationIdAndUserId(Long organizationId, Long userId);

}
