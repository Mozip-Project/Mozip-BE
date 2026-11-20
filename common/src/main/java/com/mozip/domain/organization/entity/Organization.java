package com.mozip.domain.organization.entity;

import com.mozip.domain.school.entity.School;
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

@Getter
@Entity
@Table(name = "organizations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Organization extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 50)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private OrganizationType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private OrganizationScope scope;

	// 연합이면 null
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "school_id")
	private School school;

	@Column(nullable = false)
	private boolean noTeam;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ReviewStatus reviewStatus;

	@Column(length = 500)
	private String logoUrl;

	private Organization(String name, OrganizationType type, OrganizationScope scope, School school,
		boolean noTeam) {
		this.name = name;
		this.type = type;
		this.scope = scope;
		this.school = school;
		this.noTeam = noTeam;
		this.reviewStatus = ReviewStatus.PENDING;
	}

	// 연합 단체에 학교 지정은 거부
	public static Organization create(String name, OrganizationType type, OrganizationScope scope, School school,
		boolean noTeam) {
		if (scope == OrganizationScope.UNION && school != null) {
			throw new IllegalArgumentException("연합 단체에는 학교를 지정할 수 없습니다.");
		}
		return new Organization(name, type, scope, school, noTeam);
	}

	public void updateLogo(String logoUrl) {
		this.logoUrl = logoUrl;
	}

}
