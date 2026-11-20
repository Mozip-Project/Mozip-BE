package com.mozip.domain.posting.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.entity.PostingImage;

public interface PostingImageRepository extends JpaRepository<PostingImage, Long> {

	List<PostingImage> findAllByPostingOrderBySortOrder(Posting posting);

	List<PostingImage> findAllByPostingInOrderBySortOrder(Collection<Posting> postings);

	void deleteAllByPosting(Posting posting);

}
