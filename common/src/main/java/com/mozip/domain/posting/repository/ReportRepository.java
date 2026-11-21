package com.mozip.domain.posting.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.entity.Report;
import com.mozip.domain.user.entity.User;

public interface ReportRepository extends JpaRepository<Report, Long> {

	boolean existsByUserAndPosting(User user, Posting posting);

}
