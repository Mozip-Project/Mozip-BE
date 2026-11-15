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

	private Organization(String name, OrganizationType type, OrganizationScope scope, School school,
		boolean noTeam) {
		this.name = name;
		this.type = type;
		this.scope = scope;
		this.school = school;
		this.noTeam = noTeam;
		this.reviewStatus = ReviewStatus.PENDING;
	}

	public static Organization create(String name, OrganizationType type, OrganizationScope scope, School school,
		boolean noTeam) {
		return new Organization(name, type, scope, school, noTeam);
	}

}
