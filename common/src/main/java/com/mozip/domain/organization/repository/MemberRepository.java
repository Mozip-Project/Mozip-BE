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

	// organization과 그 school까지 한 번에 로딩 (Query.me의 organizations 응답용. 그냥 findByUserId였다면
	// open-in-view=false에서 Organization이 전부 미초기화 프록시로 와서 500이 났을 것)
	@Query("""
		SELECT m FROM Member m
		JOIN FETCH m.organization o
		LEFT JOIN FETCH o.school
		WHERE m.user.id = :userId
		""")
	List<Member> findByUserId(@Param("userId") Long userId);

	boolean existsByOrganizationIdAndUserIdAndRole(Long organizationId, Long userId, MemberRole role);

	List<Member> findAllByUserId(Long userId);

	// GraphQL Me.memberships 조회, 단체, 팀, 회원, 학교를 함께 조회
	@Query("select m from Member m join fetch m.organization o left join fetch o.school join fetch m.user u "
		+ "left join fetch u.school left join fetch m.team where m.user.id = :userId order by o.id")
	List<Member> findAllWithOrganizationAndTeamByUserId(@Param("userId") Long userId);

	// GraphQL Organization.members 배치 조회, 하위 필드가 읽는 회원, 단체, 팀, 학교를 함께 조회
	@Query("select m from Member m join fetch m.user u left join fetch u.school join fetch m.organization o "
		+ "left join fetch o.school left join fetch m.team t left join fetch t.organization "
		+ "where m.organization in :organizations order by m.id")
	List<Member> findAllWithUserAndTeamByOrganizationIn(@Param("organizations") Collection<Organization> organizations);

	// 주어진 단체 중 회원이 소속된 단체 id
	@Query("select m.organization.id from Member m where m.user.id = :userId and m.organization in :organizations")
	List<Long> findOrganizationIdsByUserIdAndOrganizationIn(@Param("userId") Long userId,
		@Param("organizations") Collection<Organization> organizations);

}
