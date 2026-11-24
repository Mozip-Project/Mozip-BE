package com.mozip.domain.application.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mozip.domain.application.entity.Answer;
import com.mozip.domain.application.entity.Application;

public interface AnswerRepository extends JpaRepository<Answer, Long> {

	List<Answer> findAllByApplicationOrderById(Application application);

	List<Answer> findAllByApplicationInOrderById(Collection<Application> applications);

	void deleteAllByApplication(Application application);

}
