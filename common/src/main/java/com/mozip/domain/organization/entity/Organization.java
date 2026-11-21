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

	// 학생회가 아니면 null
	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private CouncilLevel councilLevel;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private OrganizationScope scope;

	// 연합이면 null
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "school_id")
	private School school;

	@Column(nullable = false)
	private boolean noTeam;

	// 등록 심사용 증빙 (링크 또는 업로드 파일)
	@Column(length = 500)
	private String proofLink;

	@Column(length = 500)
	private String proofFileUrl;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ReviewStatus reviewStatus;

	private Organization(String name, OrganizationType type, CouncilLevel councilLevel, OrganizationScope scope,
		School school, boolean noTeam, String proofLink, String proofFileUrl) {
		this.name = name;
		this.type = type;
		this.councilLevel = councilLevel;
		this.scope = scope;
		this.school = school;
		this.noTeam = noTeam;
		this.proofLink = proofLink;
		this.proofFileUrl = proofFileUrl;
		this.reviewStatus = ReviewStatus.PENDING;
	}

	// 학생회는 세부 구분 필수
	public static Organization create(String name, OrganizationType type, CouncilLevel councilLevel,
		OrganizationScope scope, School school, boolean noTeam, String proofLink, String proofFileUrl) {
		if ((type == OrganizationType.STUDENT_COUNCIL) != (councilLevel != null)) {
			throw new IllegalArgumentException("학생회 세부 구분은 학생회에만, 반드시 지정해야 합니다.");
		}
		if (scope == OrganizationScope.UNION && school != null) {
			throw new IllegalArgumentException("연합 단체에는 학교를 지정할 수 없습니다.");
		}
		return new Organization(name, type, councilLevel, scope, school, noTeam, proofLink, proofFileUrl);
	}

}
