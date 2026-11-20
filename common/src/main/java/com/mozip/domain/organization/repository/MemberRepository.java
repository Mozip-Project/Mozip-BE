package com.mozip.domain.organization.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mozip.domain.organization.entity.Member;

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

	// Organization.members 배치 로딩용 (@BatchMapping). user, team까지 같이 당겨와 그 하위 필드 조회 시 추가 N+1을 막음
	@Query("""
		SELECT m FROM Member m
		JOIN FETCH m.user
		LEFT JOIN FETCH m.team
		WHERE m.organization.id IN :organizationIds
		""")
	List<Member> findByOrganizationIdIn(@Param("organizationIds") List<Long> organizationIds);

}
