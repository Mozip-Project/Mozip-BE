package com.mozip.domain.organization.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mozip.domain.organization.entity.Organization;

import jakarta.persistence.LockModeType;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

	@Query("select o from Organization o left join fetch o.school where o.id = :id")
	Optional<Organization> findWithSchoolById(@Param("id") Long id);

	// 단체 행을 select for update로 잠금 (학교는 함께 조회하지 않음)
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from Organization o where o.id = :id")
	Optional<Organization> lockById(@Param("id") Long id);

	@Query("select o from Member m join m.organization o left join fetch o.school where m.user.id = :userId order by o.id")
	List<Organization> findAllByMemberUserId(@Param("userId") Long userId);

}
