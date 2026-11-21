package com.mozip.domain.posting.service;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.domain.posting.dto.PostingPage;
import com.mozip.domain.posting.dto.ScrapResult;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.entity.Scrap;
import com.mozip.domain.posting.exception.PostingErrorCode;
import com.mozip.domain.posting.repository.PostingRepository;
import com.mozip.domain.posting.repository.ScrapRepository;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScrapService {

	private static final int MAX_PAGE_SIZE = 50;

	private final ScrapRepository scrapRepository;
	private final PostingRepository postingRepository;
	private final UserRepository userRepository;

	@Transactional
	public ScrapResult toggle(Long userId, Long postingId) {
		User user = findUser(userId);
		Posting posting = findPosting(postingId);

		boolean scrapped;
		Optional<Scrap> existing = scrapRepository.findByUserAndPosting(user, posting);
		if (existing.isPresent()) {
			scrapRepository.delete(existing.get());
			scrapped = false;
		} else {
			scrapRepository.insertIfAbsent(userId, postingId);
			scrapped = true;
		}

		return new ScrapResult(scrapped, (int)scrapRepository.countByPosting(posting));
	}

	public PostingPage findMyScraps(Long userId, int page, int size) {
		if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT)
				.addContext("page", page)
				.addContext("size", size);
		}
		User user = findUser(userId);
		Page<Posting> result = scrapRepository.findPostingsByUser(user, PageRequest.of(page, size));
		return new PostingPage(result.getContent(), result.getTotalElements(), result.hasNext());
	}

	private Posting findPosting(Long postingId) {
		return postingRepository.findById(postingId)
			.orElseThrow(() -> new BusinessException(PostingErrorCode.NOT_FOUND).addContext("postingId", postingId));
	}

	private User findUser(Long userId) {
		return userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(CommonErrorCode.INVALID_INPUT).addContext("userId", userId));
	}

}
