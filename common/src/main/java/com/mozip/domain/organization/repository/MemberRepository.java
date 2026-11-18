package com.mozip.domain.organization.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.Organization;

public interface MemberRepository extends JpaRepository<Member, Long> {

	boolean existsByOrganizationIdAndUserId(Long organizationId, Long userId);

	boolean existsByOrganizationIdAndUserIdAndRole(Long organizationId, Long userId, MemberRole role);

	List<Member> findAllByUserId(Long userId);

	// GraphQL Organization.members 배치 조회, user와 team을 같이 가져와 하위 필드도 추가 쿼리 없이 응답
	@Query("select m from Member m join fetch m.user left join fetch m.team where m.organization in :organizations order by m.id")
	List<Member> findAllWithUserAndTeamByOrganizationIn(@Param("organizations") Collection<Organization> organizations);

}
