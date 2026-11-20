package com.mozip.domain.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mozip.domain.user.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByPhone(String phone);

	boolean existsByPhone(String phone);

	// GraphQL me가 lastOrganization과 그 school까지 읽으므로 같이 가져옴 (open-in-view가 꺼져 있음)
	@Query("select u from User u left join fetch u.lastOrganization o left join fetch o.school where u.id = :id")
	Optional<User> findWithLastOrganizationById(@Param("id") Long id);

}
