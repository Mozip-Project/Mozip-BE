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

// 단체가 선택한 활동 분야
@Getter
@Entity
@Table(name = "organization_fields")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrganizationFieldTag extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "organization_id", nullable = false)
	private Organization organization;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private OrganizationField field;

	private OrganizationFieldTag(Organization organization, OrganizationField field) {
		this.organization = organization;
		this.field = field;
	}

	public static OrganizationFieldTag create(Organization organization, OrganizationField field) {
		return new OrganizationFieldTag(organization, field);
	}

}
