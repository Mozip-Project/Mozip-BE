package com.mozip.domain.school.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mozip.domain.school.entity.SchoolVerification;
import com.mozip.global.common.ReviewStatus;

public interface SchoolVerificationRepository extends JpaRepository<SchoolVerification, Long> {

	boolean existsByUserIdAndStatus(Long userId, ReviewStatus status);

}
