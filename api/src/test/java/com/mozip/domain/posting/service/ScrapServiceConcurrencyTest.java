package com.mozip.domain.posting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.mozip.TestcontainersConfiguration;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.OrganizationScope;
import com.mozip.domain.organization.entity.OrganizationType;
import com.mozip.domain.organization.repository.OrganizationRepository;
import com.mozip.domain.posting.dto.ScrapResult;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.repository.PostingRepository;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;

// toggle의 bulk delete 분기가 동시 요청에서도 500(StaleObjectStateException) 없이 끝나는지 확인.
// 클래스 트랜잭션으로 묶으면 두 스레드가 같은 커넥션/트랜잭션을 타서 실제 동시 요청을 흉내낼 수 없어 @Transactional을 안 둠
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ScrapServiceConcurrencyTest {

	@Autowired
	private ScrapService scrapService;

	@Autowired
	private OrganizationRepository organizationRepository;

	@Autowired
	private PostingRepository postingRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private Organization organization;
	private User user;
	private Posting posting;

	@AfterEach
	void tearDown() {
		jdbcTemplate.update("delete from scraps where posting_id = ?", posting.getId());
		jdbcTemplate.update("delete from postings where id = ?", posting.getId());
		jdbcTemplate.update("delete from organizations where id = ?", organization.getId());
		jdbcTemplate.update("delete from users where id = ?", user.getId());
	}

	@Test
	@DisplayName("이미 스크랩한 공고에 toggle(해제) 두 요청이 동시에 들어와도 500 없이 끝난다")
	void togglingExistingScrapConcurrentlyDoesNotThrow() throws Exception {
		organization = organizationRepository.save(
			Organization.create("테스트 단체", OrganizationType.CLUB, null, OrganizationScope.CAMPUS, null, false, null, null));
		user = userRepository.save(User.create("01077770001", "encoded", "지원자", false, LocalDateTime.now()));
		posting = postingRepository.save(Posting.create(organization, "공고"));
		posting.publishNow(LocalDateTime.now());
		posting = postingRepository.save(posting);
		scrapService.toggle(user.getId(), posting.getId());

		ExecutorService executor = Executors.newFixedThreadPool(2);
		CyclicBarrier barrier = new CyclicBarrier(2);
		try {
			List<Future<ScrapResult>> futures = IntStream.range(0, 2)
				.mapToObj(i -> executor.submit(() -> {
					barrier.await();
					return scrapService.toggle(user.getId(), posting.getId());
				}))
				.toList();

			for (Future<ScrapResult> future : futures) {
				assertThatCode(() -> future.get(10, TimeUnit.SECONDS)).doesNotThrowAnyException();
			}
		} finally {
			executor.shutdownNow();
		}

		// 두 요청이 어떤 순서로 겹쳤는지와 무관하게, DB에는 0건 또는 1건만 남아야 함 (중복·오류 상태 없음)
		long remaining = jdbcTemplate.queryForObject(
			"select count(*) from scraps where user_id = ? and posting_id = ?",
			Long.class, user.getId(), posting.getId());
		assertThat(remaining).isIn(0L, 1L);
	}

}
