package com.mozip.domain.application.entity;

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

// FILE_LINK 문항 답변의 첨부. FILE은 presigned URL(purpose=APPLICATION_ATTACHMENT)로 올린 파일의 키/URL, LINK는 지원자가 직접 입력한 링크
@Getter
@Entity
@Table(name = "attachments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attachment extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "answer_id", nullable = false)
	private Answer answer;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private AttachmentType type;

	@Column(nullable = false, length = 500)
	private String url;

	@Column(length = 255)
	private String fileName;

	private Attachment(Answer answer, AttachmentType type, String url, String fileName) {
		this.answer = answer;
		this.type = type;
		this.url = url;
		this.fileName = fileName;
	}

	public static Attachment create(Answer answer, AttachmentType type, String url, String fileName) {
		return new Attachment(answer, type, url, fileName);
	}

}
