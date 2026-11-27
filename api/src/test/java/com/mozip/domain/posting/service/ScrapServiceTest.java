package com.mozip.domain.posting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.TestcontainersConfiguration;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.OrganizationScope;
import com.mozip.domain.organization.entity.OrganizationType;
import com.mozip.domain.organization.repository.OrganizationRepository;
import com.mozip.domain.posting.dto.ScrapPage;
import com.mozip.domain.posting.dto.ScrapResult;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.entity.PostingStatus;
import com.mozip.domain.posting.exception.PostingErrorCode;
import com.mozip.domain.posting.repository.PostingRepository;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.exception.BusinessException;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class ScrapServiceTest {

	@Autowired
	private ScrapService scrapService;

	@Autowired
	private OrganizationRepository organizationRepository;

	@Autowired
	private PostingRepository postingRepository;

	@Autowired
	private UserRepository userRepository;

	private Organization organization;
	private User user;

	@BeforeEach
	void setUp() {
		organization = organizationRepository.save(
			Organization.create("테스트 단체", OrganizationType.CLUB, null, OrganizationScope.CAMPUS, null, false, null, null));
		user = userRepository.save(User.create("01055550001", "encoded", "지원자", false, LocalDateTime.now()));
	}

	@Test
	@DisplayName("처음 toggleScrap하면 추가되고, 다시 toggleScrap하면 해제된다")
	void togglesScrapOnAndOff() {
		Posting posting = postingRepository.save(Posting.create(organization, "공고"));

		ScrapResult added = scrapService.toggle(user.getId(), posting.getId());
		assertThat(added.scrapped()).isTrue();
		assertThat(added.scrapCount()).isEqualTo(1);

		ScrapResult removed = scrapService.toggle(user.getId(), posting.getId());
		assertThat(removed.scrapped()).isFalse();
		assertThat(removed.scrapCount()).isEqualTo(0);
	}

	@Test
	@DisplayName("여러 회원이 스크랩하면 scrapCount에 다 반영된다")
	void scrapCountReflectsMultipleUsers() {
		Posting posting = postingRepository.save(Posting.create(organization, "공고"));
		User other = userRepository.save(User.create("01055550002", "encoded", "지원자2", false, LocalDateTime.now()));

		scrapService.toggle(user.getId(), posting.getId());
		ScrapResult result = scrapService.toggle(other.getId(), posting.getId());

		assertThat(result.scrapCount()).isEqualTo(2);
	}

	@Test
	@DisplayName("없는 공고를 스크랩하면 POSTING_404를 던진다")
	void rejectsScrapingMissingPosting() {
		assertThatThrownBy(() -> scrapService.toggle(user.getId(), 999_999L))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(PostingErrorCode.NOT_FOUND);
	}

	@Test
	@DisplayName("myScraps는 공고 상태로 거르지 않고(마감된 공고 포함) 최근 스크랩 순으로 돌려준다")
	void myScrapsIncludesAnyStatusOrderedByRecent() {
		// DRAFT 상태로만 만들지만, findPostingsByUser 쿼리 자체가 상태로 거르지 않는지를 확인하는 테스트라 상태값은 상관없음
		Posting first = postingRepository.save(Posting.create(organization, "공고1"));
		Posting second = postingRepository.save(Posting.create(organization, "공고2"));
		assertThat(first.getStatus()).isEqualTo(PostingStatus.DRAFT);

		scrapService.toggle(user.getId(), first.getId());
		scrapService.toggle(user.getId(), second.getId());

		ScrapPage page = scrapService.findMyScraps(user.getId(), 0, 20);

		assertThat(page.totalCount()).isEqualTo(2);
		assertThat(page.items()).extracting(Posting::getId)
			.containsExactly(second.getId(), first.getId());
		assertThat(page.hasNext()).isFalse();
	}

	@Test
	@DisplayName("myScraps의 size가 범위를 벗어나면 COMMON_400을 던진다")
	void rejectsInvalidPageSize() {
		assertThatThrownBy(() -> scrapService.findMyScraps(user.getId(), 0, 0))
			.isInstanceOf(BusinessException.class);
	}

}
