package com.mozip.domain.application.entity;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

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

// FILE_LINK 문항 답변의 첨부. FILE은 presigned URL(purpose=APPLICATION_ATTACHMENT)로 올린 파일의 fileKey (조회 시 GraphQL
// 리졸버가 FileService.presignGet으로 GET presigned URL을 만들어 응답), LINK는 지원자가 직접 입력한 http(s) 링크 그대로
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
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Answer answer;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private AttachmentType type;

	// TODO(#34 머지 후): FILE이면 S3 객체도 같이 지움. answer_id가 ON DELETE CASCADE라
	// ApplicationService.replaceAnswers의 벌크 삭제(answerRepository.deleteAllByApplication)로 지워질 때는
	// Hibernate가 이 행을 로드하지 않아 @PreRemove 같은 엔티티 콜백이 안 탄다. S3 삭제까지 하려면
	// 답변 교체 전에 attachment를 먼저 조회해 FileService(가칭) 삭제를 호출하거나, 벌크 삭제 대신
	// attachment를 하나씩 지우는 경로가 필요함
	@Column(nullable = false, length = 500)
	private String url;

	@Column(length = 255)
	private String fileName;

	// 바이트 크기. 입력으로 안 받고 ApplicationService가 저장 시 S3 HeadObject로 직접 확인한 값을 씀, LINK는 항상 null
	private Integer size;

	private Attachment(Answer answer, AttachmentType type, String url, String fileName, Integer size) {
		this.answer = answer;
		this.type = type;
		this.url = url;
		this.fileName = fileName;
		this.size = size;
	}

	public static Attachment create(Answer answer, AttachmentType type, String url, String fileName, Integer size) {
		return new Attachment(answer, type, url, fileName, size);
	}

}
