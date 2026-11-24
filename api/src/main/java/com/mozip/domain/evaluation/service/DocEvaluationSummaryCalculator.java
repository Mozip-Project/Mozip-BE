package com.mozip.domain.evaluation.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mozip.domain.evaluation.dto.OpinionCounts;
import com.mozip.domain.evaluation.entity.Opinion;

// 서류 평가 취합의 평균 점수, 의견 수, 순위 계산. DB 없이 값만 받아 계산함
public final class DocEvaluationSummaryCalculator {

	private DocEvaluationSummaryCalculator() {
	}

	// 지원자 한 명과 그 지원서에 저장된(SAVED) 평가들
	public record Applicant(Long applicationId, LocalDateTime submittedAt, List<SavedEvaluation> evaluations) {
	}

	// 운영진 한 명이 저장한 평가. scores는 문항별 점수이고 점수를 안 쓰는 공고면 빈 목록
	public record SavedEvaluation(Opinion opinion, List<Integer> scores) {
	}

	public record Stat(Integer rank, Double averageScore, OpinionCounts opinionCounts, boolean evaluationComplete) {
	}

	// memberCount는 공고 단체 운영진 수, useScore는 posting.docScorePerQuestion
	// 결과는 지원서 id로 찾고 순서는 넘겨받은 지원자 순서 그대로
	public static Map<Long, Stat> calculate(List<Applicant> applicants, int memberCount, boolean useScore) {
		Map<Long, Double> averages = new LinkedHashMap<>();
		Map<Long, OpinionCounts> counts = new LinkedHashMap<>();
		List<Applicant> completed = new ArrayList<>();
		for (Applicant applicant : applicants) {
			averages.put(applicant.applicationId(), useScore ? averageScore(applicant.evaluations()) : null);
			counts.put(applicant.applicationId(), opinionCounts(applicant.evaluations(), memberCount));
			if (isComplete(applicant.evaluations(), memberCount)) {
				completed.add(applicant);
			}
		}

		Map<Long, Integer> ranks = rank(completed, averages, counts, useScore);
		Map<Long, Stat> stats = new LinkedHashMap<>();
		for (Applicant applicant : applicants) {
			Long id = applicant.applicationId();
			stats.put(id, new Stat(ranks.get(id), averages.get(id), counts.get(id), ranks.containsKey(id)));
		}
		return stats;
	}

	// 운영진별 (점수 합 / 점수 개수)의 평균을 소수 둘째 자리까지 반올림. 점수가 있는 평가가 없으면 null
	// 반올림 경계에서 오차가 나지 않게 분수로 더한 뒤 마지막에 한 번만 나눔
	static Double averageScore(List<SavedEvaluation> evaluations) {
		BigInteger numerator = BigInteger.ZERO;
		BigInteger denominator = BigInteger.ONE;
		int scoredCount = 0;
		for (SavedEvaluation evaluation : evaluations) {
			List<Integer> scores = evaluation.scores();
			if (scores == null || scores.isEmpty()) {
				continue;
			}
			long sum = scores.stream().mapToLong(Integer::longValue).sum();
			BigInteger size = BigInteger.valueOf(scores.size());
			numerator = numerator.multiply(size).add(BigInteger.valueOf(sum).multiply(denominator));
			denominator = denominator.multiply(size);
			scoredCount++;
		}
		if (scoredCount == 0) {
			return null;
		}
		BigDecimal divisor = new BigDecimal(denominator.multiply(BigInteger.valueOf(scoredCount)));
		return new BigDecimal(numerator).divide(divisor, 2, RoundingMode.HALF_UP).doubleValue();
	}

	static OpinionCounts opinionCounts(List<SavedEvaluation> evaluations, int memberCount) {
		int pass = 0;
		int hold = 0;
		int fail = 0;
		for (SavedEvaluation evaluation : evaluations) {
			switch (evaluation.opinion()) {
				case PASS -> pass++;
				case HOLD -> hold++;
				case FAIL -> fail++;
			}
		}
		return new OpinionCounts(pass, hold, fail, Math.max(memberCount - evaluations.size(), 0));
	}

	// 운영진이 한 명도 없으면 완료로 보지 않음
	private static boolean isComplete(List<SavedEvaluation> evaluations, int memberCount) {
		return memberCount > 0 && evaluations.size() >= memberCount;
	}

	// 평가가 끝난 지원자끼리 1등부터 매김. 평균 높은 순, 합격 의견 많은 순, 먼저 제출한 순, 마지막은 지원서 id
	// 점수를 안 쓰는 공고는 평균을 건너뛰고 합격 의견 수부터 비교
	private static Map<Long, Integer> rank(List<Applicant> completed, Map<Long, Double> averages,
		Map<Long, OpinionCounts> counts, boolean useScore) {
		Comparator<Applicant> order = Comparator
			.comparing((Applicant a) -> counts.get(a.applicationId()).pass(), Comparator.reverseOrder())
			.thenComparing(Applicant::submittedAt, Comparator.nullsLast(Comparator.naturalOrder()))
			.thenComparing(Applicant::applicationId);
		if (useScore) {
			order = Comparator
				.comparing((Applicant a) -> averages.get(a.applicationId()),
					Comparator.nullsLast(Comparator.<Double>reverseOrder()))
				.thenComparing(order);
		}
		List<Applicant> sorted = completed.stream().sorted(order).toList();
		Map<Long, Integer> ranks = new LinkedHashMap<>();
		for (int i = 0; i < sorted.size(); i++) {
			ranks.put(sorted.get(i).applicationId(), i + 1);
		}
		return ranks;
	}

}
