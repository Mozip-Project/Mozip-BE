package com.mozip.domain.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.mozip.TestcontainersConfiguration;
import com.mozip.domain.application.entity.Application;
import com.mozip.domain.application.entity.ApplicationStatus;
import com.mozip.domain.application.repository.ApplicationRepository;
import com.mozip.domain.evaluation.DocEvaluationFixtures;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.entity.PostingStatus;
import com.mozip.domain.posting.exception.PostingConflictReason;
import com.mozip.domain.posting.exception.PostingErrorCode;
import com.mozip.domain.posting.service.PostingService;
import com.mozip.domain.school.entity.School;
import com.mozip.domain.school.repository.SchoolRepository;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.exception.BusinessException;

// 단계 전환과 지원서 제출이 겹칠 때의 순서 검증. 요청마다 트랜잭션이 따로 잡혀야 해서 @Transactional을 붙이지 않음
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ApplicationSubmitLockTest {

	@Autowired
	private ApplicationService applicationService;

	@Autowired
	private PostingService postingService;

	@Autowired
	private ApplicationRepository applicationRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private SchoolRepository schoolRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private BeanFactory beanFactory;

	private Long applicationId;

	// 롤백이 없어 남는 지원서를 지움. 다른 지원서 테스트가 지원서 테이블 전체 건수를 봄
	@AfterEach
	void deleteApplication() {
		if (applicationId != null) {
			applicationRepository.deleteById(applicationId);
		}
	}

	@Test
	@DisplayName("DOC_REVIEW로 넘기는 중에 들어온 제출은 전환이 끝날 때까지 기다렸다가 POSTING_409(NOT_RECRUITING)를 받는다")
	void submitWaitsForStatusAdvanceAndIsRejected() throws Exception {
		DocEvaluationFixtures fixtures = new DocEvaluationFixtures(beanFactory);
		Organization organization = fixtures.organization();
		User operator = fixtures.user("대표 운영진");
		fixtures.member(organization, operator, MemberRole.REPRESENTATIVE);
		Posting posting = fixtures.recruitingPosting(organization);
		User applicant = fixtures.user("지원자");
		int unique = ThreadLocalRandom.current().nextInt(100_000_000);
		applicant.verifySchool(schoolRepository.save(School.create("잠금 테스트 대학 " + unique, unique + ".test")));
		userRepository.save(applicant);
		applicationId = applicationRepository.save(Application.create(posting, null, applicant)).getId();

		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			// 단계 전환이 공고 행을 잠근 채 커밋하지 않은 상태에서 제출을 보냄
			Future<Object> submit = new TransactionTemplate(transactionManager).execute(status -> {
				postingService.advanceStatus(operator.getId(), posting.getId(), PostingStatus.DOC_REVIEW);
				Future<Object> future = executor.submit(() -> {
					try {
						return applicationService.submit(applicant.getId(), applicationId);
					} catch (BusinessException e) {
						return e;
					}
				});
				// 한쪽이라도 잠금을 안 잡으면 제출이 아직 RECRUITING으로 보고 여기서 끝나 버림
				assertThat(waitBriefly(future)).isFalse();
				return future;
			});

			Object outcome = submit.get(30, TimeUnit.SECONDS);
			assertThat(outcome).isInstanceOf(BusinessException.class);
			BusinessException exception = (BusinessException)outcome;
			assertThat(exception.getErrorCode()).isEqualTo(PostingErrorCode.CONFLICT);
			assertThat(exception.getReason()).isEqualTo(PostingConflictReason.NOT_RECRUITING.name());
			assertThat(applicationRepository.findById(applicationId).orElseThrow().getStatus())
				.isEqualTo(ApplicationStatus.DRAFT);
		} finally {
			executor.shutdownNow();
		}
	}

	private static boolean waitBriefly(Future<Object> future) {
		try {
			future.get(1, TimeUnit.SECONDS);
			return true;
		} catch (Exception e) {
			return future.isDone();
		}
	}

}
