package com.mozip.domain.posting.entity;

import java.time.LocalDateTime;

import com.mozip.domain.organization.entity.Organization;
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

// 모집 공고
@Getter
@Entity
@Table(name = "postings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Posting extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "organization_id", nullable = false)
	private Organization organization;

	@Column(nullable = false, length = 100)
	private String title;

	// 모집 안내 (HTML)
	@Column(columnDefinition = "TEXT")
	private String content;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private PostingStatus status;

	@Column(nullable = false)
	private boolean hasInterview;

	@Column(nullable = false)
	private boolean noPosition;

	// 지원 접수 시작, 게시 후 이 시각 전까지는 공고만 보이고 지원 불가
	private LocalDateTime recruitStartAt;

	private LocalDateTime recruitEndAt;

	private Posting(Organization organization, String title, boolean hasInterview, boolean noPosition) {
		this.organization = organization;
		this.title = title;
		this.status = PostingStatus.DRAFT;
		this.hasInterview = hasInterview;
		this.noPosition = noPosition;
	}

	public static Posting create(Organization organization, String title, boolean hasInterview, boolean noPosition) {
		return new Posting(organization, title, hasInterview, noPosition);
	}

}
