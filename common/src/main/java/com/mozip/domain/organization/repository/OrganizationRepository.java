package com.mozip.domain.organization.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mozip.domain.organization.entity.Organization;

import jakarta.persistence.LockModeType;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

	// open-in-view가 꺼져 있어 GraphQL이 school을 읽을 때 LAZY 예외가 나지 않게 같이 가져옴
	@Query("select o from Organization o left join fetch o.school where o.id = :id")
	Optional<Organization> findWithSchoolById(@Param("id") Long id);

	// 단체 단위로 직렬화가 필요한 쓰기(프로필 전체 교체 등)에서 select for update로 행을 잠금. outer join에는 for update를 걸 수 없어 학교는 같이 가져오지 않음
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from Organization o where o.id = :id")
	Optional<Organization> lockById(@Param("id") Long id);

}
