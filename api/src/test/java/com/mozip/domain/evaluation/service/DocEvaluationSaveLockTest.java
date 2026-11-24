package com.mozip.domain.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.mozip.TestcontainersConfiguration;
import com.mozip.domain.application.entity.Application;
import com.mozip.domain.evaluation.DocEvaluationFixtures;
import com.mozip.domain.evaluation.dto.SaveDocEvaluationInput;
import com.mozip.domain.evaluation.entity.DocEvaluation;
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

// 확정과 평가 저장이 겹칠 때의 순서 검증. 요청마다 트랜잭션이 따로 잡혀야 해서 @Transactional을 붙이지 않음
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DocEvaluationSaveLockTest {

	@Autowired
	private DocEvaluationService docEvaluationService;

	@Autowired
	private PostingRepository postingRepository;

	@Autowired
	private DocEvaluationRepository docEvaluationRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private BeanFactory beanFactory;

	// 롤백이 없어 남는 평가를 지움. 다른 평가 테스트가 평가 테이블 전체 건수를 봄
	@AfterEach
	void deleteEvaluations() {
		docEvaluationRepository.deleteAllInBatch();
	}

	@Test
	@DisplayName("확정이 진행 중일 때 들어온 평가 저장은 확정이 끝날 때까지 기다렸다가 EVALUATION_423을 받는다")
	void saveWaitsForConfirmAndIsRejected() throws Exception {
		DocEvaluationFixtures fixtures = new DocEvaluationFixtures(beanFactory);
		Organization organization = fixtures.organization();
		User operator = fixtures.user("대표 운영진");
		Member representative = fixtures.member(organization, operator, MemberRole.REPRESENTATIVE);
		Posting posting = fixtures.posting(organization, false);
		Application application = fixtures.submitted(posting, null, "지원자");
		DocEvaluation saved = fixtures.saved(application, representative, Opinion.PASS, List.of());
		SaveDocEvaluationInput input = new SaveDocEvaluationInput(application.getId(), Opinion.FAIL, List.of(), null);

		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			// 확정 UPDATE를 하고 커밋하지 않은 채로 저장 요청을 보냄. 이 UPDATE가 공고 행 잠금을 쥐고 있음
			Future<Object> save = new TransactionTemplate(transactionManager).execute(status -> {
				assertThat(postingRepository.confirmDocResult(posting.getId(), LocalDateTime.now())).isEqualTo(1);
				Future<Object> future = executor.submit(() -> {
					try {
						return docEvaluationService.save(operator.getId(), input, true);
					} catch (BusinessException e) {
						return e;
					}
				});
				// 저장이 잠금을 안 잡으면 아직 미확정으로 보고 여기서 끝나 버림
				assertThat(waitBriefly(future)).isFalse();
				return future;
			});

			Object outcome = save.get(30, TimeUnit.SECONDS);
			assertThat(outcome).isInstanceOf(BusinessException.class);
			assertThat(((BusinessException)outcome).getErrorCode()).isEqualTo(EvaluationErrorCode.RESULT_CONFIRMED);
			assertThat(docEvaluationRepository.findById(saved.getId()).orElseThrow().getOpinion())
				.isEqualTo(Opinion.PASS);
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
