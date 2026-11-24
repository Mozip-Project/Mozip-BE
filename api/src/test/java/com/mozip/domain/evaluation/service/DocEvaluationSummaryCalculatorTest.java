package com.mozip.domain.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mozip.domain.evaluation.dto.OpinionCounts;
import com.mozip.domain.evaluation.entity.Opinion;
import com.mozip.domain.evaluation.service.DocEvaluationSummaryCalculator.Applicant;
import com.mozip.domain.evaluation.service.DocEvaluationSummaryCalculator.SavedEvaluation;
import com.mozip.domain.evaluation.service.DocEvaluationSummaryCalculator.Stat;

// 평균 점수, 의견 수, 순위 계산 검증 (DB 없이)
class DocEvaluationSummaryCalculatorTest {

	private static final LocalDateTime BASE = LocalDateTime.of(2026, 11, 1, 9, 0);

	@Test
	@DisplayName("평균 점수는 운영진별 문항 평균을 다시 평균 내고 소수 둘째 자리까지 반올림한다")
	void averagesPerMemberThenRounds() {
		// 운영진 1: (5+4+4)/3 = 4.333, 운영진 2: (3+3+4)/3 = 3.333, 평균 3.833 -> 3.83
		Applicant applicant = applicant(1L, 0,
			saved(Opinion.PASS, 5, 4, 4),
			saved(Opinion.HOLD, 3, 3, 4));

		Stat stat = DocEvaluationSummaryCalculator.calculate(List.of(applicant), 2, true).get(1L);

		assertThat(stat.averageScore()).isEqualTo(3.83);
	}

	@Test
	@DisplayName("평균이 정확히 반올림 경계에 걸리면 올림한다")
	void roundsHalfUp() {
		// 운영진 1: 10/3, 운영진 2: 35/12, 평균은 정확히 3.125 -> 3.13
		Applicant thirds = applicant(1L, 0,
			saved(Opinion.PASS, 4, 3, 3),
			saved(Opinion.PASS, scoresOf(12, 3, 1, 2)));
		// (4.25 + 4.0) / 2 = 4.125 -> 4.13
		Applicant quarters = applicant(2L, 0,
			saved(Opinion.PASS, 5, 4, 4, 4),
			saved(Opinion.PASS, 4, 4));

		Map<Long, Stat> stats = DocEvaluationSummaryCalculator.calculate(List.of(thirds, quarters), 2, true);

		assertThat(stats.get(1L).averageScore()).isEqualTo(3.13);
		assertThat(stats.get(2L).averageScore()).isEqualTo(4.13);
	}

	@Test
	@DisplayName("평가가 진행 중이면 저장된 평가만으로 평균을 내고, 저장된 평가가 없으면 평균은 null이다")
	void averagesSavedOnlyWhileInProgress() {
		Applicant partly = applicant(1L, 0, saved(Opinion.PASS, 4, 5));
		Applicant none = applicant(2L, 1);

		Map<Long, Stat> stats = DocEvaluationSummaryCalculator.calculate(List.of(partly, none), 3, true);

		assertThat(stats.get(1L).averageScore()).isEqualTo(4.5);
		assertThat(stats.get(1L).evaluationComplete()).isFalse();
		assertThat(stats.get(1L).rank()).isNull();
		assertThat(stats.get(2L).averageScore()).isNull();
		assertThat(stats.get(2L).opinionCounts()).isEqualTo(new OpinionCounts(0, 0, 0, 3));
	}

	@Test
	@DisplayName("점수를 안 쓰는 공고는 점수가 저장돼 있어도 평균이 null이다")
	void noAverageWhenScoreUnused() {
		Applicant applicant = applicant(1L, 0, saved(Opinion.PASS, 5, 5));

		Stat stat = DocEvaluationSummaryCalculator.calculate(List.of(applicant), 1, false).get(1L);

		assertThat(stat.averageScore()).isNull();
		assertThat(stat.rank()).isEqualTo(1);
	}

	@Test
	@DisplayName("의견 수는 저장된 평가를 의견별로 세고 나머지 운영진은 미평가로 센다")
	void countsOpinions() {
		Applicant applicant = applicant(1L, 0,
			saved(Opinion.PASS), saved(Opinion.PASS), saved(Opinion.HOLD), saved(Opinion.FAIL));

		Stat stat = DocEvaluationSummaryCalculator.calculate(List.of(applicant), 6, false).get(1L);

		assertThat(stat.opinionCounts()).isEqualTo(new OpinionCounts(2, 1, 1, 2));
		assertThat(stat.evaluationComplete()).isFalse();
	}

	@Test
	@DisplayName("순위는 운영진 전원이 평가를 저장한 지원자끼리 평균 높은 순으로 매기고 나머지는 null이다")
	void ranksCompletedOnly() {
		Applicant low = applicant(1L, 0, saved(Opinion.PASS, 2), saved(Opinion.PASS, 2));
		Applicant high = applicant(2L, 1, saved(Opinion.FAIL, 5), saved(Opinion.FAIL, 5));
		// 평균은 가장 높지만 한 명이 아직 저장하지 않음
		Applicant incomplete = applicant(3L, 2, saved(Opinion.PASS, 5));

		Map<Long, Stat> stats = DocEvaluationSummaryCalculator.calculate(List.of(low, high, incomplete), 2, true);

		assertThat(stats.get(2L).rank()).isEqualTo(1);
		assertThat(stats.get(1L).rank()).isEqualTo(2);
		assertThat(stats.get(3L).rank()).isNull();
		assertThat(stats.get(3L).evaluationComplete()).isFalse();
		assertThat(stats.get(1L).evaluationComplete()).isTrue();
	}

	@Test
	@DisplayName("평균이 같으면 합격 의견이 많은 지원자가, 그것도 같으면 먼저 제출한 지원자가 앞 순위다")
	void breaksTies() {
		// 넷 다 평균 4.0
		Applicant onePassLate = applicant(1L, 5, saved(Opinion.PASS, 4), saved(Opinion.FAIL, 4));
		Applicant twoPass = applicant(2L, 9, saved(Opinion.PASS, 4), saved(Opinion.PASS, 4));
		Applicant onePassEarly = applicant(3L, 1, saved(Opinion.PASS, 3), saved(Opinion.HOLD, 5));
		Applicant noPass = applicant(4L, 0, saved(Opinion.HOLD, 4), saved(Opinion.FAIL, 4));

		Map<Long, Stat> stats = DocEvaluationSummaryCalculator.calculate(
			List.of(onePassLate, twoPass, onePassEarly, noPass), 2, true);

		assertThat(stats.get(2L).rank()).isEqualTo(1);
		assertThat(stats.get(3L).rank()).isEqualTo(2);
		assertThat(stats.get(1L).rank()).isEqualTo(3);
		assertThat(stats.get(4L).rank()).isEqualTo(4);
	}

	@Test
	@DisplayName("소수 셋째 자리에서만 다른 평균은 반올림하면 같으므로 동점으로 보고 합격 의견 수로 가른다")
	void tiesOnRoundedAverage() {
		// 1001/250 = 4.004 -> 4.0, 합격 0
		Applicant slightlyHigher = applicant(1L, 0, saved(Opinion.FAIL, scoresOf(250, 4, 1, 5)));
		// 4.0, 합격 1
		Applicant withPass = applicant(2L, 1, saved(Opinion.PASS, 4));

		Map<Long, Stat> stats = DocEvaluationSummaryCalculator.calculate(List.of(slightlyHigher, withPass), 1, true);

		assertThat(stats.get(1L).averageScore()).isEqualTo(4.0);
		assertThat(stats.get(2L).rank()).isEqualTo(1);
		assertThat(stats.get(1L).rank()).isEqualTo(2);
	}

	@Test
	@DisplayName("점수를 안 쓰는 공고의 순위는 합격 의견 수로 매기고 같으면 먼저 제출한 순이다")
	void ranksByPassOpinionWithoutScore() {
		Applicant onePass = applicant(1L, 0, saved(Opinion.PASS), saved(Opinion.FAIL));
		Applicant twoPassLate = applicant(2L, 7, saved(Opinion.PASS), saved(Opinion.PASS));
		Applicant twoPassEarly = applicant(3L, 3, saved(Opinion.PASS), saved(Opinion.PASS));

		Map<Long, Stat> stats = DocEvaluationSummaryCalculator.calculate(
			List.of(onePass, twoPassLate, twoPassEarly), 2, false);

		assertThat(stats.get(3L).rank()).isEqualTo(1);
		assertThat(stats.get(2L).rank()).isEqualTo(2);
		assertThat(stats.get(1L).rank()).isEqualTo(3);
	}

	@Test
	@DisplayName("모든 값이 같으면 지원서 id가 작은 쪽이 앞 순위라 순위가 겹치지 않는다")
	void fallsBackToApplicationId() {
		Applicant second = applicant(20L, 0, saved(Opinion.PASS, 3));
		Applicant first = applicant(10L, 0, saved(Opinion.PASS, 3));

		Map<Long, Stat> stats = DocEvaluationSummaryCalculator.calculate(List.of(second, first), 1, true);

		assertThat(stats.get(10L).rank()).isEqualTo(1);
		assertThat(stats.get(20L).rank()).isEqualTo(2);
	}

	@Test
	@DisplayName("운영진이 한 명도 없으면 아무도 평가 완료가 아니고 순위도 없다")
	void noMembers() {
		Applicant applicant = applicant(1L, 0);

		Stat stat = DocEvaluationSummaryCalculator.calculate(List.of(applicant), 0, true).get(1L);

		assertThat(stat.evaluationComplete()).isFalse();
		assertThat(stat.rank()).isNull();
		assertThat(stat.opinionCounts()).isEqualTo(new OpinionCounts(0, 0, 0, 0));
	}

	private static Applicant applicant(Long id, int submittedHoursLater, SavedEvaluation... evaluations) {
		return new Applicant(id, BASE.plusHours(submittedHoursLater), List.of(evaluations));
	}

	private static SavedEvaluation saved(Opinion opinion, Integer... scores) {
		return new SavedEvaluation(opinion, List.of(scores));
	}

	// 점수 total개 중 otherCount개만 other이고 나머지는 base
	private static Integer[] scoresOf(int total, int base, int otherCount, int other) {
		Integer[] scores = new Integer[total];
		Arrays.fill(scores, base);
		Arrays.fill(scores, 0, otherCount, other);
		return scores;
	}

}
