package com.mozip.domain.organization.entity;

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

// SNS/웹사이트 링크, type은 URL 호스트로 추정
@Getter
@Entity
@Table(name = "organization_links")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrganizationLink extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "organization_id", nullable = false)
	private Organization organization;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private OrganizationLinkType type;

	@Column(nullable = false, length = 500)
	private String url;

	private OrganizationLink(Organization organization, OrganizationLinkType type, String url) {
		this.organization = organization;
		this.type = type;
		this.url = url;
	}

	public static OrganizationLink create(Organization organization, OrganizationLinkType type, String url) {
		return new OrganizationLink(organization, type, url);
	}

}
