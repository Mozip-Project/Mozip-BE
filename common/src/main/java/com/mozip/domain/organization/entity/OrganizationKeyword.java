package com.mozip.domain.organization.entity;

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

// 검색 키워드, 단체당 최대 10개이고 같은 단체 안에서 중복 불가
@Getter
@Entity
@Table(name = "organization_keywords")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrganizationKeyword extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "organization_id", nullable = false)
	private Organization organization;

	@Column(nullable = false, length = 30)
	private String keyword;

	private OrganizationKeyword(Organization organization, String keyword) {
		this.organization = organization;
		this.keyword = keyword;
	}

	public static OrganizationKeyword create(Organization organization, String keyword) {
		return new OrganizationKeyword(organization, keyword);
	}

}
