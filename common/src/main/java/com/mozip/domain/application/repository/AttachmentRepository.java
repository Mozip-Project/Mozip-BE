package com.mozip.domain.application.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mozip.domain.application.entity.Answer;
import com.mozip.domain.application.entity.Attachment;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

	List<Attachment> findAllByAnswerInOrderById(Collection<Answer> answers);

}
