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

	// GraphQL Me.memberships, 단체 드롭다운이 organization / role / team을 읽으므로 한 번에 가져옴. user { school }도 LAZY 예외가 나지 않게 같이
	@Query("select m from Member m join fetch m.organization o left join fetch o.school join fetch m.user u "
		+ "left join fetch u.school left join fetch m.team where m.user.id = :userId order by o.id")
	List<Member> findAllWithOrganizationAndTeamByUserId(@Param("userId") Long userId);

	// GraphQL Organization.members 배치 조회. open-in-view가 꺼져 있어 하위 필드가 닿는 LAZY 연관(user.school, organization.school, team.organization)을 모두 같이 가져옴
	@Query("select m from Member m join fetch m.user u left join fetch u.school join fetch m.organization o "
		+ "left join fetch o.school left join fetch m.team t left join fetch t.organization "
		+ "where m.organization in :organizations order by m.id")
	List<Member> findAllWithUserAndTeamByOrganizationIn(@Param("organizations") Collection<Organization> organizations);

	// 운영진 전용 필드 접근 검사용. 요청에 들어온 단체 중 회원이 소속된 단체 id만 한 번에 조회
	@Query("select m.organization.id from Member m where m.user.id = :userId and m.organization in :organizations")
	List<Long> findOrganizationIdsByUserIdAndOrganizationIn(@Param("userId") Long userId,
		@Param("organizations") Collection<Organization> organizations);

}
