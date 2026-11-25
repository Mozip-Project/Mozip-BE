package com.mozip.domain.evaluation.entity;

// 문항 하나에 매긴 점수. doc_evaluations.scores(jsonb) 배열의 원소
public record DocScore(Long questionId, int score) {

	public static final int MIN_SCORE = 1;
	public static final int MAX_SCORE = 5;

	public DocScore {
		if (questionId == null) {
			throw new IllegalArgumentException("점수에는 문항이 있어야 합니다.");
		}
		if (score < MIN_SCORE || score > MAX_SCORE) {
			throw new IllegalArgumentException("점수는 1~5점이어야 합니다.");
		}
	}

}
