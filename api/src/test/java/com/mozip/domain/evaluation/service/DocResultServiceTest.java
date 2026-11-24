package com.mozip.domain.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.TestcontainersConfiguration;
import com.mozip.domain.application.entity.Application;
import com.mozip.domain.application.entity.DocResult;
import com.mozip.domain.application.repository.ApplicationRepository;
import com.mozip.domain.evaluation.DocEvaluationFixtures;
import com.mozip.domain.evaluation.dto.ConfirmDocResultPayload;
import com.mozip.domain.evaluation.dto.DocResultSummary;
import com.mozip.domain.evaluation.dto.SetDocResultPayload;
import com.mozip.domain.evaluation.entity.Opinion;
import com.mozip.domain.evaluation.exception.DocResultConfirmReason;
import com.mozip.domain.evaluation.exception.DocResultConflictReason;
import com.mozip.domain.evaluation.exception.EvaluationErrorCode;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.entity.PostingStatus;
import com.mozip.domain.posting.exception.PostingConflictReason;
import com.mozip.domain.posting.exception.PostingErrorCode;
import com.mozip.domain.posting.repository.PostingRepository;
import com.mozip.domain.posting.service.PostingService;
import com.mozip.domain.user.entity.User;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;
import com.mozip.global.exception.ErrorCode;

import jakarta.persistence.EntityManager;

// 서류 결과 지정과 확정의 조건 검증. 운영진은 대표자와 부원 두 명
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class DocResultServiceTest {

	@Autowired
	private DocResultService docResultService;

	@Autowired
	private PostingResultChecker postingResultChecker;

	@Autowired
	private PostingService postingService;

	@Autowired
	private PostingRepository postingRepository;

	@Autowired
	private ApplicationRepository applicationRepository;

	@Autowired
	private BeanFactory beanFactory;

	@Autowired
	private EntityManager entityManager;

	private DocEvaluationFixtures fixtures;
	private User operator;
	private Member representative;
	private Member staff;
	private Posting posting;

	@BeforeEach
	void setUp() {
		fixtures = new DocEvaluationFixtures(beanFactory);
		Organization organization = fixtures.organization();
		operator = fixtures.user("대표 운영진");
		representative = fixtures.member(organization, operator, MemberRole.REPRESENTATIVE);
		staff = fixtures.member(organization, fixtures.user("부원 운영진"), MemberRole.OPERATOR);
		posting = fixtures.posting(organization, false);
	}

	@Test
	@DisplayName("운영진 전원이 평가를 저장한 지원자에게 서류 결과를 지정하면 저장되고 현황이 같이 내려온다")
	void setsDocResult() {
		Application application = fullyEvaluated("지원자");
		flushAndClear();

		SetDocResultPayload payload = docResultService.setDocResult(operator.getId(), application.getId(),
			DocResult.PASS);

		assertThat(payload.docResult()).isEqualTo(DocResult.PASS);
		assertThat(payload.application().getId()).isEqualTo(application.getId());
		assertThat(payload.summary().totalCount()).isEqualTo(1);
		assertThat(payload.summary().passCount()).isEqualTo(1);
		assertThat(payload.summary().pendingCount()).isZero();
		assertThat(payload.summary().canConfirm()).isTrue();
		flushAndClear();
		assertThat(docResultOf(application)).isEqualTo(DocResult.PASS);
	}

	@Test
	@DisplayName("같은 서류 결과를 다시 보내도 성공하고 다른 값으로 바꿀 수도 있다")
	void setsSameDocResultAgain() {
		Application application = fullyEvaluated("지원자");
		flushAndClear();

		docResultService.setDocResult(operator.getId(), application.getId(), DocResult.HOLD);
		SetDocResultPayload again = docResultService.setDocResult(operator.getId(), application.getId(),
			DocResult.HOLD);
		assertThat(again.docResult()).isEqualTo(DocResult.HOLD);
		assertThat(again.summary().holdCount()).isEqualTo(1);
		assertThat(again.summary().canConfirm()).isFalse();

		SetDocResultPayload changed = docResultService.setDocResult(operator.getId(), application.getId(),
			DocResult.FAIL);
		assertThat(changed.docResult()).isEqualTo(DocResult.FAIL);
		assertThat(changed.summary().holdCount()).isZero();
		assertThat(changed.summary().failCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("운영진 전원이 평가를 저장하기 전에 서류 결과를 지정하면 EVALUATION_409(NOT_ALL_SAVED)를 던진다")
	void rejectsDocResultBeforeAllSaved() {
		Application onlyOneSaved = fixtures.submitted(posting, null, "한 명만 저장");
		fixtures.saved(onlyOneSaved, representative, Opinion.PASS, List.of());
		Application otherStillDrafting = fixtures.submitted(posting, null, "한 명은 작성 중");
		fixtures.saved(otherStillDrafting, representative, Opinion.PASS, List.of());
		fixtures.drafted(otherStillDrafting, staff, Opinion.PASS, List.of());
		Application nobody = fixtures.submitted(posting, null, "아무도 안 함");
		flushAndClear();

		for (Application application : List.of(onlyOneSaved, otherStillDrafting, nobody)) {
			assertBusinessException(
				() -> docResultService.setDocResult(operator.getId(), application.getId(), DocResult.PASS),
				EvaluationErrorCode.RESULT_NOT_SELECTABLE, DocResultConflictReason.NOT_ALL_SAVED);
		}
		flushAndClear();
		assertThat(docResultOf(onlyOneSaved)).isEqualTo(DocResult.PENDING);
	}

	@Test
	@DisplayName("서류 결과를 PENDING으로 지정하면 COMMON_400을 던진다")
	void rejectsPendingResult() {
		Application application = fullyEvaluated("지원자");
		flushAndClear();

		assertBusinessException(
			() -> docResultService.setDocResult(operator.getId(), application.getId(), DocResult.PENDING),
			CommonErrorCode.INVALID_INPUT, null);
	}

	@Test
	@DisplayName("없는 지원서나 제출 전 지원서에 서류 결과를 지정하면 EVALUATION_404를 던진다")
	void rejectsMissingOrDraftApplication() {
		Application draft = fixtures.draft(posting, null, "작성 중 지원자");
		flushAndClear();

		assertBusinessException(() -> docResultService.setDocResult(operator.getId(), Long.MAX_VALUE, DocResult.PASS),
			EvaluationErrorCode.APPLICATION_NOT_FOUND, null);
		assertBusinessException(() -> docResultService.setDocResult(operator.getId(), draft.getId(), DocResult.PASS),
			EvaluationErrorCode.APPLICATION_NOT_FOUND, null);
	}

	@Test
	@DisplayName("그 단체 운영진이 아니면 서류 결과 지정과 확정 모두 COMMON_403을 던진다")
	void rejectsNonOperator() {
		Application application = fullyEvaluated("지원자");
		User outsider = fixtures.user("외부인");
		User otherOperator = fixtures.user("다른 단체 운영진");
		fixtures.member(fixtures.organization(), otherOperator, MemberRole.REPRESENTATIVE);
		flushAndClear();

		for (User user : List.of(outsider, otherOperator)) {
			assertBusinessException(
				() -> docResultService.setDocResult(user.getId(), application.getId(), DocResult.PASS),
				CommonErrorCode.FORBIDDEN, null);
			assertBusinessException(() -> docResultService.confirmDocResult(user.getId(), posting.getId()),
				CommonErrorCode.FORBIDDEN, null);
		}
		flushAndClear();
		assertThat(docResultOf(application)).isEqualTo(DocResult.PENDING);
		assertThat(postingResultChecker.isDocResultConfirmed(posting.getId())).isFalse();
	}

	@Test
	@DisplayName("전원 평가가 끝나고 보류와 미선택이 없으면 서류 결과를 확정하고 확정 시각을 남긴다")
	void confirmsDocResult() {
		Application passed = fullyEvaluated("합격자");
		Application failed = fullyEvaluated("불합격자");
		flushAndClear();
		docResultService.setDocResult(operator.getId(), passed.getId(), DocResult.PASS);
		docResultService.setDocResult(operator.getId(), failed.getId(), DocResult.FAIL);
		assertThat(postingResultChecker.isDocResultConfirmed(posting.getId())).isFalse();

		ConfirmDocResultPayload payload = docResultService.confirmDocResult(operator.getId(), posting.getId());

		assertThat(payload.posting().getId()).isEqualTo(posting.getId());
		assertThat(payload.posting().getDocResultConfirmedAt()).isNotNull();
		DocResultSummary summary = payload.summary();
		assertThat(summary.docResultConfirmedAt()).isEqualTo(payload.posting().getDocResultConfirmedAt());
		assertThat(summary.totalCount()).isEqualTo(2);
		assertThat(summary.evaluatedCount()).isEqualTo(2);
		assertThat(summary.passCount()).isEqualTo(1);
		assertThat(summary.failCount()).isEqualTo(1);
		assertThat(summary.canConfirm()).isFalse();
		assertThat(postingResultChecker.isDocResultConfirmed(posting.getId())).isTrue();
	}

	@Test
	@DisplayName("합격자가 한 명도 없어도 서류 결과를 확정할 수 있다")
	void confirmsWithoutAnyPass() {
		Application failed = fullyEvaluated("불합격자");
		flushAndClear();
		docResultService.setDocResult(operator.getId(), failed.getId(), DocResult.FAIL);

		ConfirmDocResultPayload payload = docResultService.confirmDocResult(operator.getId(), posting.getId());

		assertThat(payload.summary().passCount()).isZero();
		assertThat(payload.posting().isDocResultConfirmed()).isTrue();
	}

	@Test
	@DisplayName("서류 결과를 확정해야 공고를 DOC_RESULT_NOTICE로 넘길 수 있다")
	void advancesPostingOnlyAfterConfirm() {
		Application passed = fullyEvaluated("합격자");
		flushAndClear();
		docResultService.setDocResult(operator.getId(), passed.getId(), DocResult.PASS);

		assertBusinessException(
			() -> postingService.advanceStatus(operator.getId(), posting.getId(), PostingStatus.DOC_RESULT_NOTICE),
			PostingErrorCode.CONFLICT, PostingConflictReason.DOC_RESULT_NOT_CONFIRMED);

		docResultService.confirmDocResult(operator.getId(), posting.getId());

		assertThat(postingService.advanceStatus(operator.getId(), posting.getId(), PostingStatus.DOC_RESULT_NOTICE)
			.getStatus()).isEqualTo(PostingStatus.DOC_RESULT_NOTICE);
	}

	@Test
	@DisplayName("보류가 남아 있으면 확정할 때 EVALUATION_422(HOLD_REMAINING)를 던지고 공고는 미확정으로 남는다")
	void rejectsConfirmWithHold() {
		Application passed = fullyEvaluated("합격자");
		Application held = fullyEvaluated("보류자");
		flushAndClear();
		docResultService.setDocResult(operator.getId(), passed.getId(), DocResult.PASS);
		docResultService.setDocResult(operator.getId(), held.getId(), DocResult.HOLD);

		assertBusinessException(() -> docResultService.confirmDocResult(operator.getId(), posting.getId()),
			EvaluationErrorCode.RESULT_NOT_CONFIRMABLE, DocResultConfirmReason.HOLD_REMAINING);

		assertNotConfirmed();
	}

	@Test
	@DisplayName("평가가 끝나지 않은 지원자가 남아 있으면 확정할 때 EVALUATION_422(NOT_ALL_EVALUATED)를 던진다")
	void rejectsConfirmWithUnevaluated() {
		Application passed = fullyEvaluated("합격자");
		Application unevaluated = fixtures.submitted(posting, null, "평가 덜 된 지원자");
		fixtures.saved(unevaluated, representative, Opinion.PASS, List.of());
		flushAndClear();
		docResultService.setDocResult(operator.getId(), passed.getId(), DocResult.PASS);

		assertBusinessException(() -> docResultService.confirmDocResult(operator.getId(), posting.getId()),
			EvaluationErrorCode.RESULT_NOT_CONFIRMABLE, DocResultConfirmReason.NOT_ALL_EVALUATED);

		assertNotConfirmed();
	}

	@Test
	@DisplayName("평가는 끝났지만 결과를 고르지 않은 지원자가 남아 있으면 EVALUATION_422(RESULT_NOT_SELECTED)를 던진다")
	void rejectsConfirmWithPending() {
		Application passed = fullyEvaluated("합격자");
		fullyEvaluated("결과 미선택");
		flushAndClear();
		docResultService.setDocResult(operator.getId(), passed.getId(), DocResult.PASS);

		assertBusinessException(() -> docResultService.confirmDocResult(operator.getId(), posting.getId()),
			EvaluationErrorCode.RESULT_NOT_CONFIRMABLE, DocResultConfirmReason.RESULT_NOT_SELECTED);

		assertNotConfirmed();
	}

	@Test
	@DisplayName("제출된 지원서가 없으면 확정할 때 EVALUATION_422(NO_APPLICANTS)를 던진다")
	void rejectsConfirmWithoutApplicants() {
		fixtures.draft(posting, null, "작성 중 지원자");
		flushAndClear();

		assertBusinessException(() -> docResultService.confirmDocResult(operator.getId(), posting.getId()),
			EvaluationErrorCode.RESULT_NOT_CONFIRMABLE, DocResultConfirmReason.NO_APPLICANTS);

		assertNotConfirmed();
	}

	@Test
	@DisplayName("모집 중인 공고는 조건을 다 채워도 결과 지정은 EVALUATION_409, 확정은 EVALUATION_422(NOT_DOC_REVIEW)를 던진다")
	void rejectsSetAndConfirmWhileRecruiting() {
		Posting recruiting = fixtures.recruitingPosting(posting.getOrganization());
		Application application = fixtures.submitted(recruiting, null, "모집 중 지원자");
		fixtures.saved(application, representative, Opinion.PASS, List.of());
		fixtures.saved(application, staff, Opinion.PASS, List.of());
		flushAndClear();

		assertBusinessException(
			() -> docResultService.setDocResult(operator.getId(), application.getId(), DocResult.PASS),
			EvaluationErrorCode.RESULT_NOT_SELECTABLE, DocResultConflictReason.NOT_DOC_REVIEW);
		assertBusinessException(() -> docResultService.confirmDocResult(operator.getId(), recruiting.getId()),
			EvaluationErrorCode.RESULT_NOT_CONFIRMABLE, DocResultConfirmReason.NOT_DOC_REVIEW);

		flushAndClear();
		assertThat(docResultOf(application)).isEqualTo(DocResult.PENDING);
		assertThat(postingResultChecker.isDocResultConfirmed(recruiting.getId())).isFalse();
	}

	@Test
	@DisplayName("없는 공고를 확정하면 POSTING_404를 던진다")
	void rejectsConfirmOfMissingPosting() {
		assertBusinessException(() -> docResultService.confirmDocResult(operator.getId(), Long.MAX_VALUE),
			PostingErrorCode.NOT_FOUND, null);
	}

	@Test
	@DisplayName("확정한 뒤에는 서류 결과를 바꾸거나 다시 확정할 수 없고 EVALUATION_423을 던진다")
	void rejectsChangeAfterConfirm() {
		Application passed = fullyEvaluated("합격자");
		flushAndClear();
		docResultService.setDocResult(operator.getId(), passed.getId(), DocResult.PASS);
		docResultService.confirmDocResult(operator.getId(), posting.getId());

		assertBusinessException(() -> docResultService.setDocResult(operator.getId(), passed.getId(), DocResult.FAIL),
			EvaluationErrorCode.RESULT_CONFIRMED, null);
		// 같은 값을 다시 보내는 것도 확정 뒤에는 막힘
		assertBusinessException(() -> docResultService.setDocResult(operator.getId(), passed.getId(), DocResult.PASS),
			EvaluationErrorCode.RESULT_CONFIRMED, null);
		assertBusinessException(() -> docResultService.confirmDocResult(operator.getId(), posting.getId()),
			EvaluationErrorCode.RESULT_CONFIRMED, null);

		flushAndClear();
		assertThat(docResultOf(passed)).isEqualTo(DocResult.PASS);
	}

	// 운영진 두 명이 모두 평가를 저장한 지원서
	private Application fullyEvaluated(String applicantName) {
		Application application = fixtures.submitted(posting, null, applicantName);
		fixtures.saved(application, representative, Opinion.PASS, List.of());
		fixtures.saved(application, staff, Opinion.HOLD, List.of());
		return application;
	}

	private DocResult docResultOf(Application application) {
		return applicationRepository.findById(application.getId()).orElseThrow().getDocResult();
	}

	private void assertNotConfirmed() {
		flushAndClear();
		assertThat(postingRepository.findById(posting.getId()).orElseThrow().getDocResultConfirmedAt()).isNull();
		assertThat(postingResultChecker.isDocResultConfirmed(posting.getId())).isFalse();
	}

	private void assertBusinessException(ThrowingCallable call, ErrorCode errorCode, Enum<?> reason) {
		assertThatThrownBy(call)
			.isInstanceOf(BusinessException.class)
			.satisfies(e -> {
				assertThat(((BusinessException)e).getErrorCode()).isEqualTo(errorCode);
				assertThat(((BusinessException)e).getReason()).isEqualTo(reason == null ? null : reason.name());
			});
	}

	// DB에 실제로 쓴 값을 서비스가 다시 읽게 함 (jsonb 점수 컬럼 포함)
	private void flushAndClear() {
		entityManager.flush();
		entityManager.clear();
	}

}
