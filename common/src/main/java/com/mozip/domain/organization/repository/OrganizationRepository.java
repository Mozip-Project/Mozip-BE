package com.mozip.domain.organization.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mozip.domain.organization.entity.Organization;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

	// school까지 같이 로딩 (GraphQL로 Organization을 돌려줄 때 open-in-view=false라 미리 로딩해야 함)
	@Query("SELECT o FROM Organization o LEFT JOIN FETCH o.school WHERE o.id = :id")
	Optional<Organization> findByIdWithSchool(@Param("id") Long id);

	@Query("select o from Organization o left join fetch o.school where o.id = :id")
	Optional<Organization> findWithSchoolById(@Param("id") Long id);

	@Query("select o from Member m join m.organization o left join fetch o.school where m.user.id = :userId order by o.id")
	List<Organization> findAllByMemberUserId(@Param("userId") Long userId);

}
