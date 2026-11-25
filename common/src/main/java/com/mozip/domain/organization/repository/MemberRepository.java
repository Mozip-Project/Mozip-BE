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

// 조회는 모두 소속 중(leftAt is null)인 행만
public interface MemberRepository extends JpaRepository<Member, Long> {

	boolean existsByOrganizationIdAndUserIdAndLeftAtIsNull(Long organizationId, Long userId);

	boolean existsByOrganizationIdAndUserIdAndRoleAndLeftAtIsNull(Long organizationId, Long userId, MemberRole role);

	// organization과 그 school까지 한 번에 로딩 (Query.me의 organizations 응답용. 그냥 findByUserId였다면
	// open-in-view=false에서 Organization이 전부 미초기화 프록시로 와서 500이 났을 것)
	@Query("""
		SELECT m FROM Member m
		JOIN FETCH m.organization o
		LEFT JOIN FETCH o.school
		WHERE m.user.id = :userId AND m.leftAt IS NULL
		""")
	List<Member> findByUserId(@Param("userId") Long userId);

	// 소속 중인 운영진의 단체 id (단체 행을 잠그기 전에 어느 단체인지 알아내는 용도)
	@Query("select m.organization.id from Member m where m.id = :id and m.leftAt is null")
	Optional<Long> findOrganizationIdByActiveId(@Param("id") Long id);

	List<Member> findAllByUserIdAndLeftAtIsNull(Long userId);

	boolean existsByUserIdAndLeftAtIsNull(Long userId);

	// 단체와 회원을 함께 조회
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

	// 주어진 단체 중 회원이 소속된 단체 id
	@Query("select m.organization.id from Member m where m.user.id = :userId and m.organization in :organizations "
		+ "and m.leftAt is null")
	List<Long> findOrganizationIdsByUserIdAndOrganizationIn(@Param("userId") Long userId,
		@Param("organizations") Collection<Organization> organizations);

	// 운영진 관리 표: 대표자 먼저, 그 뒤 합류일 오름차순. 하위 필드가 읽는 회원, 단체, 팀, 학교를 함께 조회
	@Query("select m from Member m join fetch m.user u left join fetch u.school join fetch m.organization o "
		+ "left join fetch o.school left join fetch m.team t left join fetch t.organization "
		+ "where m.organization.id = :organizationId and m.leftAt is null "
		+ "order by case when m.role = com.mozip.domain.organization.entity.MemberRole.REPRESENTATIVE then 0 else 1 end, "
		+ "m.createdAt, m.id")
	List<Member> findAllActiveWithUserAndTeamByOrganizationId(@Param("organizationId") Long organizationId);

}
