package com.mozip.domain.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.mozip.TestcontainersConfiguration;
import com.mozip.domain.application.entity.Application;
import com.mozip.domain.application.entity.DocResult;
import com.mozip.domain.evaluation.DocEvaluationFixtures;
import com.mozip.domain.evaluation.entity.Opinion;
import com.mozip.domain.evaluation.exception.EvaluationErrorCode;
import com.mozip.domain.evaluation.repository.DocEvaluationRepository;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.repository.PostingRepository;
import com.mozip.domain.user.entity.User;
import com.mozip.global.exception.BusinessException;

// 서류 결과 동시 확정 검증. 요청마다 트랜잭션이 따로 잡혀야 해서 @Transactional을 붙이지 않음
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DocResultConfirmConcurrencyTest {

	private static final int REQUESTS = 4;

	@Autowired
	private DocResultService docResultService;

	@Autowired
	private PostingRepository postingRepository;

	@Autowired
	private DocEvaluationRepository docEvaluationRepository;

	@Autowired
	private BeanFactory beanFactory;

	// 롤백이 없어 남는 평가를 지움. 다른 평가 테스트가 평가 테이블 전체 건수를 봄
	@AfterEach
	void deleteEvaluations() {
		docEvaluationRepository.deleteAllInBatch();
	}

	@Test
	@DisplayName("여러 운영진이 동시에 확정하면 한 요청만 성공하고 나머지는 EVALUATION_423을 받는다")
	void confirmsOnlyOnce() throws Exception {
		DocEvaluationFixtures fixtures = new DocEvaluationFixtures(beanFactory);
		Organization organization = fixtures.organization();
		User operator = fixtures.user("대표 운영진");
		Member representative = fixtures.member(organization, operator, MemberRole.REPRESENTATIVE);
		Posting posting = fixtures.posting(organization, false);
		Application application = fixtures.submitted(posting, null, "지원자");
		fixtures.saved(application, representative, Opinion.PASS, List.of());
		docResultService.setDocResult(operator.getId(), application.getId(), DocResult.PASS);

		ExecutorService executor = Executors.newFixedThreadPool(REQUESTS);
		CountDownLatch ready = new CountDownLatch(REQUESTS);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Object>> futures = new ArrayList<>();
		try {
			for (int i = 0; i < REQUESTS; i++) {
				Callable<Object> request = () -> {
					ready.countDown();
					start.await();
					try {
						return docResultService.confirmDocResult(operator.getId(), posting.getId());
					} catch (BusinessException e) {
						return e;
					}
				};
				futures.add(executor.submit(request));
			}
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			int succeeded = 0;
			List<BusinessException> rejected = new ArrayList<>();
			for (Future<Object> future : futures) {
				Object outcome = future.get(30, TimeUnit.SECONDS);
				if (outcome instanceof BusinessException e) {
					rejected.add(e);
				} else {
					succeeded++;
				}
			}

			assertThat(succeeded).isEqualTo(1);
			assertThat(rejected).hasSize(REQUESTS - 1)
				.allSatisfy(e -> assertThat(e.getErrorCode()).isEqualTo(EvaluationErrorCode.RESULT_CONFIRMED));
			assertThat(postingRepository.findById(posting.getId()).orElseThrow().isDocResultConfirmed()).isTrue();
		} finally {
			executor.shutdownNow();
		}
	}

}
