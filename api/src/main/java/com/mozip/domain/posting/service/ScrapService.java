package com.mozip.domain.posting.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.domain.posting.dto.ScrapPage;
import com.mozip.domain.posting.dto.ScrapResult;
import com.mozip.domain.posting.entity.Posting;
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
		findUser(userId);
		Posting posting = findPosting(postingId);

		// 삭제 건수로 분기: 있었으면(1건 삭제) 해제, 없었으면(0건 삭제) 추가.
		// 엔티티를 읽어서 지우는 방식은 동시에 같은 (user, posting)로 toggle이 들어오면 늦게 도착한 쪽이
		// 이미 지워진 행을 지우려다 StaleObjectStateException(500)이 났음. bulk delete는 건수만 돌려주니 안전함
		boolean scrapped = scrapRepository.deleteByUserIdAndPostingId(userId, postingId) == 0;
		if (scrapped) {
			scrapRepository.insertIfAbsent(userId, postingId);
		}

		return new ScrapResult(scrapped, (int)scrapRepository.countByPosting(posting));
	}

	public ScrapPage findMyScraps(Long userId, int page, int size) {
		if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT)
				.addContext("page", page)
				.addContext("size", size);
		}
		User user = findUser(userId);
		Page<Posting> result = scrapRepository.findPostingsByUser(user, PageRequest.of(page, size));
		return new ScrapPage(result.getContent(), result.getTotalElements(), result.hasNext());
	}

	private Posting findPosting(Long postingId) {
		return postingRepository.findById(postingId)
			.orElseThrow(() -> new BusinessException(PostingErrorCode.NOT_FOUND).addContext("postingId", postingId));
	}

	// 토큰의 회원이 DB에 없는 건 입력 실수가 아니라 세션이 더는 유효하지 않다는 뜻이라 401로 응답
	// FK 값만 필요해서 findById 대신 getReferenceById로 불필요한 컬럼 조회를 줄임
	private User findUser(Long userId) {
		if (!userRepository.existsById(userId)) {
			throw new BusinessException(CommonErrorCode.UNAUTHORIZED).addContext("userId", userId);
		}
		return userRepository.getReferenceById(userId);
	}

}
