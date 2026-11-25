package com.mozip.domain.user.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mozip.domain.user.entity.Experience;

public interface ExperienceRepository extends JpaRepository<Experience, Long> {

	// GraphQL User.experiences 배치 조회용 (@BatchMapping)
	@Query("select e from Experience e where e.user.id in :userIds order by e.id")
	List<Experience> findByUserIdIn(@Param("userIds") Collection<Long> userIds);

}
