package com.mozip.domain.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mozip.domain.user.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByPhone(String phone);

	boolean existsByPhone(String phone);

	// school까지 한 번에 로딩 (Query.me 응답 조립용, open-in-view=false라 서비스 메서드 끝나기 전에 미리 로딩해야 함)
	@Query("""
		SELECT u FROM User u
		LEFT JOIN FETCH u.school
		WHERE u.id = :id
		""")
	Optional<User> findByIdWithAssociations(@Param("id") Long id);

}
