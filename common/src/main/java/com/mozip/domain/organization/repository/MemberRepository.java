package com.mozip.domain.organization.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.Organization;

// 내보내진 운영진(leftAt not null)은 소속이 아니므로 모든 조회에 leftAt is null 조건을 붙임
public interface MemberRepository extends JpaRepository<Member, Long> {

	boolean existsByOrganizationIdAndUserIdAndLeftAtIsNull(Long organizationId, Long userId);

	boolean existsByOrganizationIdAndUserIdAndRoleAndLeftAtIsNull(Long organizationId, Long userId, MemberRole role);

	List<Member> findAllByUserIdAndLeftAtIsNull(Long userId);

	// 내보내기가 organization(권한 검사)과 user(lastOrganization 정리)를 바로 읽으므로 한 번에 가져옴
	@Query("select m from Member m join fetch m.organization join fetch m.user where m.id = :id and m.leftAt is null")
	Optional<Member> findActiveWithOrganizationAndUserById(@Param("id") Long id);

	// GraphQL Me.memberships, 단체 드롭다운이 organization / role / team을 읽으므로 한 번에 가져옴
	@Query("select m from Member m join fetch m.organization o left join fetch o.school join fetch m.user left join fetch m.team where m.user.id = :userId and m.leftAt is null order by o.id")
	List<Member> findAllWithOrganizationAndTeamByUserId(@Param("userId") Long userId);

	// GraphQL Organization.members 배치 조회, user와 team을 같이 가져와 하위 필드도 추가 쿼리 없이 응답
	@Query("select m from Member m join fetch m.user left join fetch m.team where m.organization in :organizations and m.leftAt is null order by m.id")
	List<Member> findAllWithUserAndTeamByOrganizationIn(@Param("organizations") Collection<Organization> organizations);

	// 운영진 관리 표: 대표자 먼저, 그 뒤 합류일 오름차순
	@Query("select m from Member m join fetch m.user left join fetch m.team where m.organization.id = :organizationId and m.leftAt is null order by case when m.role = com.mozip.domain.organization.entity.MemberRole.REPRESENTATIVE then 0 else 1 end, m.createdAt, m.id")
	List<Member> findAllActiveWithUserAndTeamByOrganizationId(@Param("organizationId") Long organizationId);

}
