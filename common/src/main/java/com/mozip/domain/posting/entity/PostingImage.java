package com.mozip.domain.posting.entity;

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

@Getter
@Entity
@Table(name = "posting_images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostingImage extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "posting_id", nullable = false)
	private Posting posting;

	@Column(nullable = false, length = 500)
	private String url;

	// 대표 이미지 (전체 공고 카드 썸네일), 안 고르면 첫 번째 사진
	@Column(nullable = false)
	private boolean isRepresentative;

	@Column(nullable = false)
	private int sortOrder;

	private PostingImage(Posting posting, String url, int sortOrder) {
		this.posting = posting;
		this.url = url;
		this.isRepresentative = false;
		this.sortOrder = sortOrder;
	}

	public static PostingImage create(Posting posting, String url, int sortOrder) {
		return new PostingImage(posting, url, sortOrder);
	}

	public void markRepresentative() {
		this.isRepresentative = true;
	}

}
