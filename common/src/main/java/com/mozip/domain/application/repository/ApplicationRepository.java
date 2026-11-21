package com.mozip.domain.application.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mozip.domain.application.entity.Application;
import com.mozip.domain.application.entity.ApplicationStatus;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.user.entity.User;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

	Optional<Application> findByPostingAndUser(Posting posting, User user);

	List<Application> findAllByUserOrderByCreatedAtDesc(User user);

	List<Application> findAllByUserAndStatusOrderByCreatedAtDesc(User user, ApplicationStatus status);

	// B의 서류 평가 대상 조회용 연결 함수(getSubmittedApplications)에서 씀
	List<Application> findAllByPostingAndStatus(Posting posting, ApplicationStatus status);

}
