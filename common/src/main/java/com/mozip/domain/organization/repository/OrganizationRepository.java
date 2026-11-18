package com.mozip.domain.organization.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mozip.domain.organization.entity.Organization;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

	// open-in-view가 꺼져 있어 GraphQL이 school을 읽을 때 LAZY 예외가 나지 않게 같이 가져옴
	@Query("select o from Organization o left join fetch o.school where o.id = :id")
	Optional<Organization> findWithSchoolById(@Param("id") Long id);

	// 회원이 운영진(Member)으로 들어가 있는 단체를 id 순으로
	@Query("select o from Member m join m.organization o left join fetch o.school where m.user.id = :userId order by o.id")
	List<Organization> findAllByMemberUserId(@Param("userId") Long userId);

}
