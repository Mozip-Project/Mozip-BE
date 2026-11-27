package com.mozip.domain.posting.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.entity.Report;
import com.mozip.domain.user.entity.User;

public interface ReportRepository extends JpaRepository<Report, Long> {

	boolean existsByUserAndPosting(User user, Posting posting);

	// 공고 삭제 시 같이 지움 (PostingService.delete). FK(fk_reports_posting_id)만 있고 cascade는 없음
	void deleteAllByPosting(Posting posting);

}
