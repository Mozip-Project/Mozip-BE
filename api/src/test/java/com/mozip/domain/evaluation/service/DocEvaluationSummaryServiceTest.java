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
import com.mozip.domain.evaluation.DocEvaluationFixtures;
import com.mozip.domain.evaluation.dto.DocEvaluationSummaryPage;
import com.mozip.domain.evaluation.dto.DocEvaluationSummaryRow;
import com.mozip.domain.evaluation.dto.DocResultSummary;
import com.mozip.domain.evaluation.dto.DocResultTab;
import com.mozip.domain.evaluation.dto.DocResultTabCounts;
import com.mozip.domain.evaluation.dto.DocSummarySort;
import com.mozip.domain.evaluation.dto.OpinionCounts;
import com.mozip.domain.evaluation.entity.DocEvaluation;
import com.mozip.domain.evaluation.entity.DocScore;
import com.mozip.domain.evaluation.entity.Opinion;
import com.mozip.domain.evaluation.exception.EvaluationErrorCode;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.posting.entity.Position;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.entity.Question;
import com.mozip.domain.posting.exception.PostingErrorCode;
import com.mozip.domain.user.entity.User;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;
import com.mozip.global.exception.ErrorCode;

import jakarta.persistence.EntityManager;

// 서류 평가 취합 조회 검증. 점수를 쓰는 공고에 포지션 둘(개발, 디자인), 문항 둘, 운영진 둘을 두고 지원자 다섯 명을 만든다
// 김하나: 개발, 평균 4.5, 합격 의견 2, 결과 PASS
// 이두리: 개발, 평균 3.5, 합격 의견 1, 결과 HOLD
// 박세찬: 디자인, 평균 3.5, 합격 의견 0, 결과 FAIL
// 최하늘: 디자인, 한 명만 저장해 평균 5.0, 결과 미선택
// 정다운: 개발, 평가 없음, 결과 미선택
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class DocEvaluationSummaryServiceTest {

	@Autowired
	private DocEvaluationSummaryService docEvaluationSummaryService;

	@Autowired
	private BeanFactory beanFactory;

	@Autowired
	private EntityManager entityManager;

	private DocEvaluationFixtures fixtures;
	private Organization organization;
	private User operator;
	private Member representative;
	private Member staff;
	private Posting posting;
	private Position development;
	private Position design;
	private Question firstQuestion;
	private Question secondQuestion;
	private Application hana;
	private Application duri;
	private Application sechan;
	private Application haneul;
	private Application daun;

	@BeforeEach
	void setUp() {
		fixtures = new DocEvaluationFixtures(beanFactory);
		organization = fixtures.organization();
		operator = fixtures.user("대표 운영진");
		representative = fixtures.member(organization, operator, MemberRole.REPRESENTATIVE);
		staff = fixtures.member(organization, fixtures.user("부원 운영진"), MemberRole.OPERATOR);
		posting = fixtures.posting(organization, true);
		development = fixtures.position(posting, "개발", 0);
		design = fixtures.position(posting, "디자인", 1);
		firstQuestion = fixtures.question(posting, 0);
		secondQuestion = fixtures.question(posting, 1);

		hana = fixtures.submitted(posting, development, "김하나");
		fixtures.saved(hana, representative, Opinion.PASS, scores(5, 5));
		fixtures.saved(hana, staff, Opinion.PASS, scores(4, 4));
		hana.decideDocResult(DocResult.PASS);

		duri = fixtures.submitted(posting, development, "이두리");
		fixtures.saved(duri, representative, Opinion.PASS, scores(4, 4));
		fixtures.saved(duri, staff, Opinion.FAIL, scores(3, 3));
		duri.decideDocResult(DocResult.HOLD);

		sechan = fixtures.submitted(posting, design, "박세찬");
		fixtures.saved(sechan, representative, Opinion.HOLD, scores(3, 4));
		fixtures.saved(sechan, staff, Opinion.HOLD, scores(3, 4));
		sechan.decideDocResult(DocResult.FAIL);

		haneul = fixtures.submitted(posting, design, "최하늘");
		fixtures.saved(haneul, representative, Opinion.PASS, scores(5, 5));
		fixtures.drafted(haneul, staff, Opinion.FAIL, scores(1, 1));

		daun = fixtures.submitted(posting, development, "정다운");

		// 제출 전 지원서는 어디에도 세지 않음
		fixtures.draft(posting, development, "작성 중 지원자");
		flushAndClear();
	}

	@Test
	@DisplayName("취합 목록은 제출된 지원서만 평균 점수 높은 순으로 내려주고 평균, 의견 수, 순위, 실제 서류 결과를 담는다")
	void findsSummary() {
		DocEvaluationSummaryPage page = find(null, null, null, null, null);

		assertThat(names(page)).containsExactly("최하늘", "김하나", "이두리", "박세찬", "정다운");
		assertThat(page.totalCount()).isEqualTo(5);
		assertThat(page.page()).isEqualTo(1);
		assertThat(page.totalPages()).isEqualTo(1);

		DocEvaluationSummaryRow hanaRow = rowOf(page, hana);
		assertThat(hanaRow.averageScore()).isEqualTo(4.5);
		assertThat(hanaRow.rank()).isEqualTo(1);
		assertThat(hanaRow.opinionCounts()).isEqualTo(new OpinionCounts(2, 0, 0, 0));
		assertThat(hanaRow.evaluationComplete()).isTrue();
		assertThat(hanaRow.docResult()).isEqualTo(DocResult.PASS);

		// 평균이 같은 두 명은 합격 의견이 많은 쪽이 앞 순위
		assertThat(rowOf(page, duri).averageScore()).isEqualTo(3.5);
		assertThat(rowOf(page, duri).rank()).isEqualTo(2);
		assertThat(rowOf(page, duri).opinionCounts()).isEqualTo(new OpinionCounts(1, 0, 1, 0));
		assertThat(rowOf(page, sechan).averageScore()).isEqualTo(3.5);
		assertThat(rowOf(page, sechan).rank()).isEqualTo(3);
		assertThat(rowOf(page, sechan).docResult()).isEqualTo(DocResult.FAIL);

		// 한 명만 저장한 지원자는 저장된 평가만으로 평균을 내고 순위는 없음. 작성 중인 평가는 세지 않음
		DocEvaluationSummaryRow haneulRow = rowOf(page, haneul);
		assertThat(haneulRow.averageScore()).isEqualTo(5.0);
		assertThat(haneulRow.rank()).isNull();
		assertThat(haneulRow.opinionCounts()).isEqualTo(new OpinionCounts(1, 0, 0, 1));
		assertThat(haneulRow.evaluationComplete()).isFalse();
		assertThat(haneulRow.docResult()).isEqualTo(DocResult.PENDING);

		DocEvaluationSummaryRow daunRow = rowOf(page, daun);
		assertThat(daunRow.averageScore()).isNull();
		assertThat(daunRow.rank()).isNull();
		assertThat(daunRow.opinionCounts()).isEqualTo(new OpinionCounts(0, 0, 0, 2));
	}

	@Test
	@DisplayName("현황은 공고 전체 기준이고 평가가 덜 끝났거나 보류, 미선택이 있으면 canConfirm이 false다")
	void summarizesWholePosting() {
		DocResultSummary summary = find(DocResultTab.PASS, List.of(design.getId()), "없는 이름", null, null).summary();

		assertThat(summary.totalCount()).isEqualTo(5);
		assertThat(summary.evaluatedCount()).isEqualTo(3);
		assertThat(summary.allEvaluated()).isFalse();
		assertThat(summary.passCount()).isEqualTo(1);
		assertThat(summary.holdCount()).isEqualTo(1);
		assertThat(summary.failCount()).isEqualTo(1);
		assertThat(summary.pendingCount()).isEqualTo(2);
		assertThat(summary.canConfirm()).isFalse();
		assertThat(summary.docResultConfirmedAt()).isNull();
	}

	@Test
	@DisplayName("탭을 고르면 그 결과의 지원자만 나오고 PENDING은 ALL 탭에만 나온다. 탭별 건수는 탭과 무관하다")
	void filtersByTab() {
		DocEvaluationSummaryPage pass = find(DocResultTab.PASS, null, null, null, null);
		assertThat(names(pass)).containsExactly("김하나");
		assertThat(pass.totalCount()).isEqualTo(1);
		assertThat(pass.tabCounts()).isEqualTo(new DocResultTabCounts(5, 1, 1, 1));

		assertThat(names(find(DocResultTab.HOLD, null, null, null, null))).containsExactly("이두리");
		assertThat(names(find(DocResultTab.FAIL, null, null, null, null))).containsExactly("박세찬");
		assertThat(names(find(DocResultTab.ALL, null, null, null, null))).contains("최하늘", "정다운");
	}

	@Test
	@DisplayName("포지션으로 거르면 건수와 탭별 건수에 반영되고 포지션별 건수와 순위는 그대로다")
	void filtersByPosition() {
		DocEvaluationSummaryPage page = find(null, List.of(design.getId()), null, null, null);

		assertThat(names(page)).containsExactly("최하늘", "박세찬");
		assertThat(page.totalCount()).isEqualTo(2);
		assertThat(page.tabCounts()).isEqualTo(new DocResultTabCounts(2, 0, 0, 1));
		assertThat(page.positionCounts()).extracting(count -> count.position().getName())
			.containsExactly("개발", "디자인");
		assertThat(page.positionCounts()).extracting(count -> count.count()).containsExactly(3, 2);
		assertThat(rowOf(page, sechan).rank()).isEqualTo(3);

		assertThat(find(null, List.of(development.getId(), design.getId()), null, null, null).totalCount())
			.isEqualTo(5);
	}

	@Test
	@DisplayName("이름으로 검색하면 건수, 탭별 건수, 포지션별 건수에 모두 반영된다")
	void searchesByName() {
		DocEvaluationSummaryPage page = find(null, null, " 하 ", null, null);

		assertThat(names(page)).containsExactly("최하늘", "김하나");
		assertThat(page.totalCount()).isEqualTo(2);
		assertThat(page.tabCounts()).isEqualTo(new DocResultTabCounts(2, 1, 0, 0));
		assertThat(page.positionCounts()).extracting(count -> count.count()).containsExactly(1, 1);
		assertThat(page.summary().totalCount()).isEqualTo(5);

		assertThat(find(null, null, "   ", null, null).totalCount()).isEqualTo(5);
	}

	@Test
	@DisplayName("정렬 기준에 따라 순서가 바뀌고 점수가 없는 지원자는 점수 정렬에서 항상 뒤로 간다")
	void sorts() {
		assertThat(names(find(null, null, null, DocSummarySort.SCORE_DESC, null)))
			.containsExactly("최하늘", "김하나", "이두리", "박세찬", "정다운");
		assertThat(names(find(null, null, null, DocSummarySort.SCORE_ASC, null)))
			.containsExactly("이두리", "박세찬", "김하나", "최하늘", "정다운");
		assertThat(names(find(null, null, null, DocSummarySort.NAME_ASC, null)))
			.containsExactly("김하나", "박세찬", "이두리", "정다운", "최하늘");
		assertThat(names(find(null, null, null, DocSummarySort.NAME_DESC, null)))
			.containsExactly("최하늘", "정다운", "이두리", "박세찬", "김하나");
		// 합격 의견 수가 같으면 먼저 제출한 순
		assertThat(names(find(null, null, null, DocSummarySort.PASS_OPINION_DESC, null)))
			.containsExactly("김하나", "이두리", "최하늘", "박세찬", "정다운");
	}

	@Test
	@DisplayName("한 페이지는 10건이고 범위를 넘는 페이지는 빈 목록이다")
	void paginates() {
		Posting bigPosting = fixtures.posting(organization, false);
		for (int i = 1; i <= 12; i++) {
			fixtures.submitted(bigPosting, null, "지원자 " + String.format("%02d", i));
		}
		flushAndClear();

		DocEvaluationSummaryPage first = docEvaluationSummaryService.findSummary(operator.getId(),
			bigPosting.getId(), null, null, null, DocSummarySort.NAME_ASC, 1);
		DocEvaluationSummaryPage second = docEvaluationSummaryService.findSummary(operator.getId(),
			bigPosting.getId(), null, null, null, DocSummarySort.NAME_ASC, 2);
		DocEvaluationSummaryPage third = docEvaluationSummaryService.findSummary(operator.getId(),
			bigPosting.getId(), null, null, null, DocSummarySort.NAME_ASC, 3);

		assertThat(first.rows()).hasSize(10);
		assertThat(names(first).getFirst()).isEqualTo("지원자 01");
		assertThat(first.totalCount()).isEqualTo(12);
		assertThat(first.totalPages()).isEqualTo(2);
		assertThat(names(second)).containsExactly("지원자 11", "지원자 12");
		assertThat(second.page()).isEqualTo(2);
		assertThat(third.rows()).isEmpty();
		assertThat(third.totalCount()).isEqualTo(12);
	}

	@Test
	@DisplayName("점수를 안 쓰는 공고는 평균이 없고 기본 정렬과 순위를 합격 의견 수로 정한다. 포지션 없는 모집은 포지션별 건수가 빈 배열이다")
	void usesPassOpinionWithoutScore() {
		Posting noScorePosting = fixtures.posting(organization, false);
		Application onePass = fixtures.submitted(noScorePosting, null, "합격 의견 하나");
		fixtures.saved(onePass, representative, Opinion.PASS, List.of());
		fixtures.saved(onePass, staff, Opinion.FAIL, List.of());
		Application twoPass = fixtures.submitted(noScorePosting, null, "합격 의견 둘");
		fixtures.saved(twoPass, representative, Opinion.PASS, List.of());
		fixtures.saved(twoPass, staff, Opinion.PASS, List.of());
		flushAndClear();

		DocEvaluationSummaryPage page = docEvaluationSummaryService.findSummary(operator.getId(),
			noScorePosting.getId(), null, null, null, null, null);

		assertThat(names(page)).containsExactly("합격 의견 둘", "합격 의견 하나");
		assertThat(rowOf(page, twoPass).rank()).isEqualTo(1);
		assertThat(rowOf(page, onePass).rank()).isEqualTo(2);
		assertThat(rowOf(page, twoPass).averageScore()).isNull();
		assertThat(page.positionCounts()).isEmpty();
		assertThat(page.summary().allEvaluated()).isTrue();
		// 평가는 끝났지만 결과를 아직 안 골랐음
		assertThat(page.summary().canConfirm()).isFalse();
	}

	@Test
	@DisplayName("지원자가 없는 공고의 취합 목록은 비어 있고 확정할 수 없다")
	void emptyPosting() {
		Posting emptyPosting = fixtures.posting(organization, true);
		flushAndClear();

		DocEvaluationSummaryPage page = docEvaluationSummaryService.findSummary(operator.getId(),
			emptyPosting.getId(), null, null, null, null, null);

		assertThat(page.rows()).isEmpty();
		assertThat(page.totalCount()).isZero();
		assertThat(page.totalPages()).isZero();
		assertThat(page.tabCounts()).isEqualTo(new DocResultTabCounts(0, 0, 0, 0));
		assertThat(page.summary().totalCount()).isZero();
		assertThat(page.summary().canConfirm()).isFalse();
	}

	@Test
	@DisplayName("page가 1보다 작거나 이 공고의 포지션이 아닌 id로 거르면 COMMON_400을 던진다")
	void rejectsInvalidInput() {
		Posting otherPosting = fixtures.posting(organization, true);
		Position otherPosition = fixtures.position(otherPosting, "다른 공고 포지션", 0);
		flushAndClear();

		assertBusinessException(() -> find(null, null, null, null, 0), CommonErrorCode.INVALID_INPUT);
		assertBusinessException(() -> find(null, null, null, null, -1), CommonErrorCode.INVALID_INPUT);
		assertBusinessException(
			() -> find(null, List.of(design.getId(), otherPosition.getId()), null, null, null),
			CommonErrorCode.INVALID_INPUT);
	}

	@Test
	@DisplayName("그 단체 운영진이 아니면 취합 목록과 평가 목록 모두 COMMON_403을 던지고, 없는 공고는 POSTING_404를 던진다")
	void rejectsNonOperatorAndMissingPosting() {
		User outsider = fixtures.user("외부인");
		flushAndClear();

		assertBusinessException(() -> docEvaluationSummaryService.findSummary(outsider.getId(), posting.getId(),
			null, null, null, null, null), CommonErrorCode.FORBIDDEN);
		assertBusinessException(() -> docEvaluationSummaryService.findEvaluations(outsider.getId(), hana.getId()),
			CommonErrorCode.FORBIDDEN);
		assertBusinessException(() -> docEvaluationSummaryService.findSummary(operator.getId(), Long.MAX_VALUE,
			null, null, null, null, null), PostingErrorCode.NOT_FOUND);
	}

	@Test
	@DisplayName("지원서의 평가 목록은 저장된 평가만 운영진, 의견, 메모, 문항별 점수와 함께 내려준다")
	void findsEvaluations() {
		List<DocEvaluation> evaluations = docEvaluationSummaryService.findEvaluations(operator.getId(), hana.getId());

		assertThat(evaluations).hasSize(2);
		DocEvaluation first = evaluations.getFirst();
		assertThat(first.getMember().getId()).isEqualTo(representative.getId());
		assertThat(first.getMember().getUser().getName()).isEqualTo("대표 운영진");
		assertThat(first.getOpinion()).isEqualTo(Opinion.PASS);
		assertThat(first.getMemo()).isEqualTo("PASS 의견 메모");
		assertThat(first.getSubmittedAt()).isNotNull();
		assertThat(first.getScores()).containsExactly(new DocScore(firstQuestion.getId(), 5),
			new DocScore(secondQuestion.getId(), 5));
		assertThat(evaluations.get(1).getScores()).containsExactly(new DocScore(firstQuestion.getId(), 4),
			new DocScore(secondQuestion.getId(), 4));

		// 작성 중인 평가는 빠짐
		assertThat(docEvaluationSummaryService.findEvaluations(operator.getId(), haneul.getId()))
			.extracting(evaluation -> evaluation.getMember().getId())
			.containsExactly(representative.getId());
		assertThat(docEvaluationSummaryService.findEvaluations(operator.getId(), daun.getId())).isEmpty();
	}

	@Test
	@DisplayName("없는 지원서나 제출 전 지원서의 평가 목록을 조회하면 EVALUATION_404를 던진다")
	void rejectsEvaluationsOfMissingOrDraftApplication() {
		Application draft = fixtures.draft(posting, null, "또 다른 작성 중 지원자");
		flushAndClear();

		assertBusinessException(() -> docEvaluationSummaryService.findEvaluations(operator.getId(), Long.MAX_VALUE),
			EvaluationErrorCode.APPLICATION_NOT_FOUND);
		assertBusinessException(() -> docEvaluationSummaryService.findEvaluations(operator.getId(), draft.getId()),
			EvaluationErrorCode.APPLICATION_NOT_FOUND);
	}

	private DocEvaluationSummaryPage find(DocResultTab tab, List<Long> positionIds, String keyword,
		DocSummarySort sort, Integer page) {
		return docEvaluationSummaryService.findSummary(operator.getId(), posting.getId(), tab, positionIds, keyword,
			sort, page);
	}

	private List<DocScore> scores(int first, int second) {
		return List.of(new DocScore(firstQuestion.getId(), first), new DocScore(secondQuestion.getId(), second));
	}

	private static List<String> names(DocEvaluationSummaryPage page) {
		return page.rows().stream().map(row -> row.application().getUser().getName()).toList();
	}

	private static DocEvaluationSummaryRow rowOf(DocEvaluationSummaryPage page, Application application) {
		return page.rows().stream()
			.filter(row -> row.application().getId().equals(application.getId()))
			.findFirst()
			.orElseThrow();
	}

	private void assertBusinessException(ThrowingCallable call, ErrorCode errorCode) {
		assertThatThrownBy(call)
			.isInstanceOf(BusinessException.class)
			.satisfies(e -> {
				assertThat(((BusinessException)e).getErrorCode()).isEqualTo(errorCode);
				assertThat(((BusinessException)e).getReason()).isNull();
			});
	}

	private void flushAndClear() {
		entityManager.flush();
		entityManager.clear();
	}

}
