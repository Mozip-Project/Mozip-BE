package com.mozip.domain.posting.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.entity.Question;

public interface QuestionRepository extends JpaRepository<Question, Long> {

	List<Question> findAllByPostingOrderBySortOrder(Posting posting);

	List<Question> findAllByPostingInOrderBySortOrder(Collection<Posting> postings);

	void deleteAllByPosting(Posting posting);

}
