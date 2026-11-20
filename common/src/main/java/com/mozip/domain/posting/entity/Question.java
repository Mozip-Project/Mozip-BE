package com.mozip.domain.posting.entity;

import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.mozip.global.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 지원서 문항
@Getter
@Entity
@Table(name = "questions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Question extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "posting_id", nullable = false)
	private Posting posting;

	// 적용 포지션, null = 공통 문항
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "position_id")
	private Position position;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private QuestionType type;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(length = 500)
	private String description;

	// TODO(B 서류 평가): 평가 화면 표에 쓸 짧은 제목. 아직 입력 경로가 없어 컬럼만 둠 (ab-boundary.md 요청)
	@Column(length = 50)
	private String shortTitle;

	@Column(nullable = false)
	private boolean required;

	// TEXT 글자 수 제한
	private Integer maxLength;

	// SINGLE_CHOICE / MULTI_CHOICE 보기
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(columnDefinition = "text[]")
	private List<String> choices;

	// TABLE 열 이름
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(columnDefinition = "text[]")
	private List<String> columns;

	// TABLE 최대 행
	private Integer maxRows;

	// FILE_LINK 최대 개수
	private Integer maxFiles;

	@Column(nullable = false)
	private int sortOrder;

	private Question(Posting posting, Position position, QuestionType type, String title, String description,
		boolean required, Integer maxLength, List<String> choices, List<String> columns, Integer maxRows,
		Integer maxFiles, int sortOrder) {
		this.posting = posting;
		this.position = position;
		this.type = type;
		this.title = title;
		this.description = description;
		this.required = required;
		this.maxLength = maxLength;
		this.choices = choices;
		this.columns = columns;
		this.maxRows = maxRows;
		this.maxFiles = maxFiles;
		this.sortOrder = sortOrder;
	}

	public static Question create(Posting posting, Position position, QuestionType type, String title,
		String description, boolean required, Integer maxLength, List<String> choices, List<String> columns,
		Integer maxRows, Integer maxFiles, int sortOrder) {
		return new Question(posting, position, type, title, description, required, maxLength, choices, columns,
			maxRows, maxFiles, sortOrder);
	}

	public QuestionOptions getOptions() {
		return QuestionOptions.from(this);
	}

}
