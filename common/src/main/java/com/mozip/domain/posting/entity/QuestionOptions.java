package com.mozip.domain.posting.entity;

import java.util.List;

// 문항 유형별 설정 (해당 유형 값만 채움). Question 엔티티 컬럼을 그대로 보여주는 값 객체라 별도 테이블 없음
public record QuestionOptions(
	List<String> choices,
	List<String> columns,
	Integer maxRows,
	Integer maxFiles
) {

	public static QuestionOptions from(Question question) {
		if (question.getChoices() == null && question.getColumns() == null && question.getMaxRows() == null
			&& question.getMaxFiles() == null) {
			return null;
		}
		return new QuestionOptions(question.getChoices(), question.getColumns(), question.getMaxRows(),
			question.getMaxFiles());
	}

}
