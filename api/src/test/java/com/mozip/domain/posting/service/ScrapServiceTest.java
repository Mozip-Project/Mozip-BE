package com.mozip.domain.posting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import com.mozip.global.exception.CommonErrorCode;

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
		Posting posting = publishedPosting("공고");

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
		Posting posting = publishedPosting("공고");
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
	@DisplayName("토큰의 회원이 DB에 없으면 COMMON_401을 던진다")
	void rejectsScrapingWithMissingUser() {
		Posting posting = postingRepository.save(Posting.create(organization, "공고"));

		assertThatThrownBy(() -> scrapService.toggle(999_999L, posting.getId()))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.UNAUTHORIZED));
	}

	@Test
	@DisplayName("myScraps는 공고 상태로 거르지 않고(마감된 공고 포함) 최근 스크랩 순으로 돌려준다")
	void myScrapsIncludesAnyStatusOrderedByRecent() {
		Posting first = publishedPosting("공고1");
		Posting second = publishedPosting("공고2");
		// 마감(ENDED)된 공고도 포함되는지 같이 확인
		second.advanceStatus(PostingStatus.DOC_REVIEW, LocalDateTime.now());
		second.advanceStatus(PostingStatus.DOC_RESULT_NOTICE, LocalDateTime.now());
		second.advanceStatus(PostingStatus.FINAL_RESULT, LocalDateTime.now());
		second.advanceStatus(PostingStatus.ENDED, LocalDateTime.now());

		scrapService.toggle(user.getId(), first.getId());
		scrapService.toggle(user.getId(), second.getId());

		ScrapPage page = scrapService.findMyScraps(user.getId(), 0, 20);

		assertThat(page.totalCount()).isEqualTo(2);
		assertThat(page.items()).extracting(Posting::getId)
			.containsExactly(second.getId(), first.getId());
		assertThat(page.hasNext()).isFalse();
	}

	@Test
	@DisplayName("아직 게시 전(DRAFT)인 공고를 스크랩하면 POSTING_404를 던진다")
	void rejectsScrapingDraftPosting() {
		Posting draft = postingRepository.save(Posting.create(organization, "초안 공고"));

		assertThatThrownBy(() -> scrapService.toggle(user.getId(), draft.getId()))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(PostingErrorCode.NOT_FOUND));
	}

	@ParameterizedTest
	@DisplayName("myScraps의 page나 size가 범위를 벗어나면 COMMON_400을 던진다")
	@CsvSource({"0, 0", "0, 51", "-1, 20"})
	void rejectsInvalidPageSize(int page, int size) {
		assertThatThrownBy(() -> scrapService.findMyScraps(user.getId(), page, size))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
	}

	// toggle은 DRAFT를 POSTING_404로 막아서, 스크랩 가능한 공고가 필요한 테스트는 게시까지 해 둠
	private Posting publishedPosting(String title) {
		Posting posting = postingRepository.save(Posting.create(organization, title));
		posting.publishNow(LocalDateTime.now());
		return posting;
	}

}
