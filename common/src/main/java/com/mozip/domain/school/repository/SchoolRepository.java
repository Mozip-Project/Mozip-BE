package com.mozip.domain.school.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mozip.domain.school.entity.School;

public interface SchoolRepository extends JpaRepository<School, Long> {

	Optional<School> findByEmailDomain(String emailDomain);

	// 단체 만들기, 학교 인증 화면 자동완성. 결과가 너무 많아지지 않게 상위 20개만
	List<School> findTop20ByNameContainingIgnoreCaseOrderByName(String keyword);

}
