package com.mozip.domain.application.entity;

import com.mozip.domain.posting.entity.Question;
import com.mozip.global.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

// 지원서 문항별 답변
@Getter
@Entity
@Table(name = "answers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Answer extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "application_id", nullable = false)
	private Application application;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "question_id", nullable = false)
	private Question question;

	// TEXT는 그대로, SINGLE_CHOICE는 선택지 문자열 그대로, MULTI_CHOICE와 TABLE은 JSON 배열 문자열
	@Column(columnDefinition = "TEXT")
	private String content;

	private Answer(Application application, Question question, String content) {
		this.application = application;
		this.question = question;
		this.content = content;
	}

	public static Answer create(Application application, Question question, String content) {
		return new Answer(application, question, content);
	}

}
