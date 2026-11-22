package com.mozip.domain.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mozip.domain.user.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByPhone(String phone);

	// 학교를 함께 조회
	@Query("select u from User u left join fetch u.school where u.id = :id")
	Optional<User> findWithSchoolById(@Param("id") Long id);

	@Query("select u from User u left join fetch u.school where u.phone = :phone")
	Optional<User> findWithSchoolByPhone(@Param("phone") String phone);

	boolean existsByPhone(String phone);

}
