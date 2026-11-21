package com.mozip.domain.posting.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

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
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.entity.PostingStatus;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class PostingRepositoryTest {

	@Autowired
	private PostingRepository postingRepository;

	@Autowired
	private OrganizationRepository organizationRepository;

	private Organization organization;

	@BeforeEach
	void setUp() {
		organization = organizationRepository.save(
			Organization.create("테스트 단체", OrganizationType.CLUB, OrganizationScope.CAMPUS, null, true));
	}

	@Test
	@DisplayName("closeRecruitingScheduled은 recruitEndAt이 지난 RECRUITING만 DOC_REVIEW로 바꾸고, 다시 돌리면 0건이다")
	void closesOnlyDueRecruitingPostings() {
		Posting due = draft("마감 시간 지남");
		due.updateBasicStep("마감 시간 지남", "본문", null, null, null, true, false, null, null, null,
			null, LocalDateTime.now().minusMinutes(1));
		due.publishNow();

		Posting notDue = draft("마감 안 지남");
		notDue.updateBasicStep("마감 안 지남", "본문", null, null, null, true, false, null, null, null,
			null, LocalDateTime.now().plusDays(1));
		notDue.publishNow();

		LocalDateTime now = LocalDateTime.now();
		int firstRun = postingRepository.closeRecruitingScheduled(now);
		assertThat(firstRun).isEqualTo(1);

		assertThat(postingRepository.findById(due.getId()).orElseThrow().getStatus())
			.isEqualTo(PostingStatus.DOC_REVIEW);
		assertThat(postingRepository.findById(notDue.getId()).orElseThrow().getStatus())
			.isEqualTo(PostingStatus.RECRUITING);

		int secondRun = postingRepository.closeRecruitingScheduled(LocalDateTime.now());
		assertThat(secondRun).isEqualTo(0);
	}

	private Posting draft(String title) {
		return postingRepository.save(Posting.create(organization, title));
	}

}
