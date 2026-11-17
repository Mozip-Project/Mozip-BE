package com.mozip.domain.posting.entity;

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

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private boolean required;

	@Column(nullable = false)
	private int sortOrder;

	private Question(Posting posting, Position position, QuestionType type, String title, boolean required,
		int sortOrder) {
		this.posting = posting;
		this.position = position;
		this.type = type;
		this.title = title;
		this.required = required;
		this.sortOrder = sortOrder;
	}

	public static Question create(Posting posting, Position position, QuestionType type, String title,
		boolean required, int sortOrder) {
		return new Question(posting, position, type, title, required, sortOrder);
	}

}
