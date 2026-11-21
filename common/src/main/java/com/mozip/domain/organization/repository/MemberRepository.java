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

	boolean existsByUserIdAndLeftAtIsNull(Long userId);

	// 내보내기가 organization(권한 검사)과 user(lastOrganization 정리)를 바로 읽으므로 한 번에 가져옴
	@Query("select m from Member m join fetch m.organization join fetch m.user where m.id = :id and m.leftAt is null")
	Optional<Member> findActiveWithOrganizationAndUserById(@Param("id") Long id);

	// GraphQL Me.memberships 조회, 단체, 팀, 회원, 학교를 함께 조회
	@Query("select m from Member m join fetch m.organization o left join fetch o.school join fetch m.user u "
		+ "left join fetch u.school left join fetch m.team where m.user.id = :userId and m.leftAt is null order by o.id")
	List<Member> findAllWithOrganizationAndTeamByUserId(@Param("userId") Long userId);

	// GraphQL Organization.members 배치 조회, 하위 필드가 읽는 회원, 단체, 팀, 학교를 함께 조회
	@Query("select m from Member m join fetch m.user u left join fetch u.school join fetch m.organization o "
		+ "left join fetch o.school left join fetch m.team t left join fetch t.organization "
		+ "where m.organization in :organizations and m.leftAt is null order by m.id")
	List<Member> findAllWithUserAndTeamByOrganizationIn(@Param("organizations") Collection<Organization> organizations);

	// 운영진 전용 필드 접근 검사용. 요청에 들어온 단체 중 회원이 소속된 단체 id만 한 번에 조회
	@Query("select m.organization.id from Member m where m.user.id = :userId and m.organization in :organizations "
		+ "and m.leftAt is null")
	List<Long> findOrganizationIdsByUserIdAndOrganizationIn(@Param("userId") Long userId,
		@Param("organizations") Collection<Organization> organizations);

	// 운영진 관리 표: 대표자 먼저, 그 뒤 합류일 오름차순
	@Query("select m from Member m join fetch m.user left join fetch m.team where m.organization.id = :organizationId and m.leftAt is null order by case when m.role = com.mozip.domain.organization.entity.MemberRole.REPRESENTATIVE then 0 else 1 end, m.createdAt, m.id")
	List<Member> findAllActiveWithUserAndTeamByOrganizationId(@Param("organizationId") Long organizationId);

}
