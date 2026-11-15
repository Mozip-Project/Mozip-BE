package com.mozip.domain.school.entity;

import com.mozip.global.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "schools")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class School extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String name;

	// 학교 메일 인증 도메인 (예: skku.edu)
	@Column(length = 100)
	private String emailDomain;

	private School(String name, String emailDomain) {
		this.name = name;
		this.emailDomain = emailDomain;
	}

	public static School create(String name, String emailDomain) {
		return new School(name, emailDomain);
	}

}
